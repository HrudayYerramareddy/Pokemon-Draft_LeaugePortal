package com.pokemondraft.leagueportal.service;

import com.pokemondraft.leagueportal.model.*;
import com.pokemondraft.leagueportal.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class WeeklyLineupService {
    private final WeeklySubmissionRepository submissions; private final RosterEntryRepository roster; private final MatchupRepository matchups; private final LeagueSettingsRepository settings; private final PokemonRepository pokemon;
    public WeeklyLineupService(WeeklySubmissionRepository submissions,RosterEntryRepository roster,MatchupRepository matchups,LeagueSettingsRepository settings,PokemonRepository pokemon){this.submissions=submissions;this.roster=roster;this.matchups=matchups;this.settings=settings;this.pokemon=pokemon;}

    @Transactional
    public WeeklySubmission submit(Long teamId,List<Long> ids){
        LeagueSettings s=settings.findById(1L).orElseThrow(); int week=s.getCurrentWeek(); finalizeExpired(week);
        if(s.getLineupDeadline()!=null&&!LocalDateTime.now().isBefore(s.getLineupDeadline()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Lineup deadline has passed");
        if(ids==null||ids.size()!=6||new HashSet<>(ids).size()!=6)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Select exactly 6 unique Pokemon");
        Matchup m=matchupFor(teamId,week); Long opponent=m.getHomeTeamId().equals(teamId)?m.getAwayTeamId():m.getHomeTeamId();
        if(submissions.findByTeamIdAndWeek(opponent,week).isPresent()&&submissions.findByTeamIdAndWeek(teamId,week).isPresent())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Both teams submitted; this matchup is locked");
        Set<Long> mine=roster.findByTeamId(teamId).stream().map(RosterEntry::getPokemonId).collect(Collectors.toSet());if(!mine.containsAll(ids))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Lineup contains a Pokemon not on your roster");
        String csv=ids.stream().map(String::valueOf).collect(Collectors.joining(","));WeeklySubmission ws=submissions.findByTeamIdAndWeek(teamId,week).orElse(new WeeklySubmission(teamId,week,csv));ws.setPokemonIdsCsv(csv);ws.setSubmittedAt(LocalDateTime.now());ws.setAutomatic(false);return submissions.save(ws);
    }

    @Transactional
    public void finalizeExpired(int week){LeagueSettings s=settings.findById(1L).orElse(null);if(s==null||s.getLineupDeadline()==null||LocalDateTime.now().isBefore(s.getLineupDeadline()))return;for(Matchup m:matchups.findAllByOrderByWeekAscIdAsc())if(m.getWeek()==week){ensureRandom(m.getHomeTeamId(),week);ensureRandom(m.getAwayTeamId(),week);}}
    private void ensureRandom(Long teamId,int week){if(submissions.findByTeamIdAndWeek(teamId,week).isPresent())return;List<RosterEntry> entries=new ArrayList<>(roster.findByTeamId(teamId));if(entries.size()<6)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Team "+teamId+" has fewer than 6 Pokemon");Collections.shuffle(entries,new Random(Objects.hash(teamId,week,2026)));String csv=entries.stream().limit(6).map(e->String.valueOf(e.getPokemonId())).collect(Collectors.joining(","));WeeklySubmission ws=new WeeklySubmission(teamId,week,csv);ws.setAutomatic(true);ws.setSubmittedAt(LocalDateTime.now());submissions.save(ws);}
    public Map<String,Object> matchupLineups(Matchup m){finalizeExpired(m.getWeek());Optional<WeeklySubmission> h=submissions.findByTeamIdAndWeek(m.getHomeTeamId(),m.getWeek()),a=submissions.findByTeamIdAndWeek(m.getAwayTeamId(),m.getWeek());boolean reveal=h.isPresent()&&a.isPresent();Map<String,Object> out=new LinkedHashMap<>();out.put("revealed",reveal);out.put("homeSubmitted",h.isPresent());out.put("awaySubmitted",a.isPresent());if(reveal){out.put("homeLineup",names(h.get()));out.put("awayLineup",names(a.get()));out.put("homeAutomatic",h.get().isAutomatic());out.put("awayAutomatic",a.get().isAutomatic());}return out;}
    public boolean isLocked(Long teamId,int week){Matchup m=matchupFor(teamId,week);return submissions.findByTeamIdAndWeek(m.getHomeTeamId(),week).isPresent()&&submissions.findByTeamIdAndWeek(m.getAwayTeamId(),week).isPresent();}
    private Matchup matchupFor(Long teamId,int week){return matchups.findAllByOrderByWeekAscIdAsc().stream().filter(m->m.getWeek()==week&&(m.getHomeTeamId().equals(teamId)||m.getAwayTeamId().equals(teamId))).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"No matchup for this team this week"));}
    private List<Map<String,Object>> names(WeeklySubmission s){Map<Long,Pokemon> all=pokemon.findAll().stream().collect(Collectors.toMap(Pokemon::getId,p->p));List<Map<String,Object>> out=new ArrayList<>();for(String x:s.getPokemonIdsCsv().split(",")){Pokemon p=all.get(Long.valueOf(x));if(p!=null)out.add(Map.of("pokemonId",p.getId(),"name",p.getName(),"price",p.getPrice()));}return out;}
}
