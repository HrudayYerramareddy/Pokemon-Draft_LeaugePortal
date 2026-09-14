package com.pokemondraft.leagueportal.service;

import com.pokemondraft.leagueportal.model.*;
import com.pokemondraft.leagueportal.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class DraftService {
    private final TeamRepository teams; private final PokemonRepository pokemon; private final DraftPickRepository picks;
    private final RosterEntryRepository roster; private final LeagueSettingsRepository settings;
    public DraftService(TeamRepository teams,PokemonRepository pokemon,DraftPickRepository picks,RosterEntryRepository roster,LeagueSettingsRepository settings){this.teams=teams;this.pokemon=pokemon;this.picks=picks;this.roster=roster;this.settings=settings;}

    public Team currentTeam(){
        List<Team> order=teams.findAll(); order.sort(Comparator.comparingInt(Team::getDraftPosition));
        if(order.size()!=16) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Draft requires 16 teams");
        long count=picks.count(); if(count>=160) return null;
        int round=(int)(count/16); int slot=(int)(count%16);
        boolean snake=settings.findById(1L).orElseGet(LeagueSettings::new).isSnakeDraft();
        if(snake && round%2==1) slot=15-slot;
        return order.get(slot);
    }

    @Transactional
    public DraftPick pick(Long actingTeamId,boolean manager,Long pokemonId){
        LeagueSettings s=settings.findById(1L).orElseGet(LeagueSettings::new);
        if(!s.isDraftOpen()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Draft is closed");
        Team current=currentTeam();
        if(current==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Draft is complete");
        if(!manager && !current.getId().equals(actingTeamId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"It is not your turn");
        Pokemon p=pokemon.findById(pokemonId).orElseThrow();
        if(p.isDrafted() || roster.findByPokemonId(pokemonId).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Pokemon already drafted");
        if(roster.countByTeamId(current.getId())>=10) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Roster already has 10 Pokemon");
        int overall=(int)picks.count()+1; int round=(overall-1)/16+1;
        p.setDrafted(true); pokemon.save(p); roster.save(new RosterEntry(current.getId(),p.getId()));
        return picks.save(new DraftPick(overall,round,current.getId(),p.getId()));
    }

    @Transactional
    public void undoLast(){
        List<DraftPick> all=picks.findAllByOrderByOverallPickAsc(); if(all.isEmpty()) return;
        DraftPick last=all.get(all.size()-1);
        roster.findByPokemonId(last.getPokemonId()).ifPresent(roster::delete);
        pokemon.findById(last.getPokemonId()).ifPresent(p->{p.setDrafted(false);pokemon.save(p);});
        picks.delete(last);
    }
}
