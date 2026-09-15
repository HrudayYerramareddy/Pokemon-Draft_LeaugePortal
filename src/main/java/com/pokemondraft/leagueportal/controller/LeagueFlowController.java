package com.pokemondraft.leagueportal.controller;

import com.pokemondraft.leagueportal.model.*;
import com.pokemondraft.leagueportal.repository.*;
import com.pokemondraft.leagueportal.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/api/flow")
public class LeagueFlowController {
    private final AuthService auth; private final WeeklyLineupService lineups; private final MatchupRepository matchups; private final TeamRepository teams; private final LeagueSettingsRepository settings; private final RosterEntryRepository roster; private final PokemonRepository pokemon; private final FreeAgencyService freeAgency;
    public LeagueFlowController(AuthService auth,WeeklyLineupService lineups,MatchupRepository matchups,TeamRepository teams,LeagueSettingsRepository settings,RosterEntryRepository roster,PokemonRepository pokemon,FreeAgencyService freeAgency){this.auth=auth;this.lineups=lineups;this.matchups=matchups;this.teams=teams;this.settings=settings;this.roster=roster;this.pokemon=pokemon;this.freeAgency=freeAgency;}
    public record LineupRequest(List<Long> pokemonIds){}
    @GetMapping("/lineup") public Map<String,Object> lineup(HttpSession session){AppUser u=auth.current(session);if(u.getRole()==Role.MANAGER)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Manager has no lineup");LeagueSettings s=settings.findById(1L).orElseThrow();lineups.finalizeExpired(s.getCurrentWeek());Map<String,Object> m=new LinkedHashMap<>();m.put("week",s.getCurrentWeek());m.put("deadline",s.getLineupDeadline());m.put("locked",lineups.isLocked(u.getTeamId(),s.getCurrentWeek()));m.put("roster",rosterView(u.getTeamId()));return m;}
    @PostMapping("/lineup") public WeeklySubmission submit(@RequestBody LineupRequest r,HttpSession session){AppUser u=auth.current(session);if(u.getRole()==Role.MANAGER)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Manager has no lineup");return lineups.submit(u.getTeamId(),r.pokemonIds());}
    @GetMapping("/schedule") public List<Map<String,Object>> schedule(HttpSession session){auth.current(session);Map<Long,String> names=new HashMap<>();for(Team t:teams.findAll())names.put(t.getId(),t.getName());List<Map<String,Object>> out=new ArrayList<>();for(Matchup x:matchups.findAllByOrderByWeekAscIdAsc()){Map<String,Object> m=new LinkedHashMap<>();m.put("id",x.getId());m.put("week",x.getWeek());m.put("homeTeamId",x.getHomeTeamId());m.put("awayTeamId",x.getAwayTeamId());m.put("homeTeam",names.get(x.getHomeTeamId()));m.put("awayTeam",names.get(x.getAwayTeamId()));m.put("homeScore",x.getHomeScore());m.put("awayScore",x.getAwayScore());m.put("played",x.isPlayed());m.putAll(lineups.matchupLineups(x));out.add(m);}return out;}
    public record FaabTradeRequest(Long recipientTeamId,Long offeredPokemonId,Long requestedPokemonId,Integer proposerFaab,Integer recipientFaab){}
    @PostMapping("/trades") public TradeOffer trade(@RequestBody FaabTradeRequest r,HttpSession session){AppUser u=auth.current(session);if(u.getRole()==Role.MANAGER)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Manager cannot offer trade");return freeAgency.offerTrade(u.getTeamId(),r.recipientTeamId(),r.offeredPokemonId(),r.requestedPokemonId(),r.proposerFaab()==null?0:r.proposerFaab(),r.recipientFaab()==null?0:r.recipientFaab());}
    private List<Map<String,Object>> rosterView(Long teamId){List<Map<String,Object>> out=new ArrayList<>();for(RosterEntry e:roster.findByTeamId(teamId)){Pokemon p=pokemon.findById(e.getPokemonId()).orElse(null);if(p!=null)out.add(Map.of("pokemonId",p.getId(),"name",p.getName(),"price",p.getPrice()));}return out;}
}
