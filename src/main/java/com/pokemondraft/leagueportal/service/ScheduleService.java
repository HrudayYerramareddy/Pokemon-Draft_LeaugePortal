package com.pokemondraft.leagueportal.service;

import com.pokemondraft.leagueportal.model.*;
import com.pokemondraft.leagueportal.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class ScheduleService {
    private final TeamRepository teams;
    private final MatchupRepository matchups;
    private final LeagueSettingsRepository settingsRepo;
    public ScheduleService(TeamRepository teams, MatchupRepository matchups, LeagueSettingsRepository settingsRepo){
        this.teams=teams;this.matchups=matchups;this.settingsRepo=settingsRepo;
    }

    @Transactional
    public void generate(){
        List<Team> a=teams.findByDivision("A");
        List<Team> b=teams.findByDivision("B");
        if(a.size()!=8 || b.size()!=8) throw new IllegalStateException("Schedule requires exactly 8 teams in each division");
        Comparator<Team> order=Comparator.comparingInt(Team::getDraftPosition).thenComparing(Team::getId);
        a.sort(order);b.sort(order);
        matchups.deleteAll();
        addRoundRobin(a,1);
        addRoundRobin(b,1);
        LeagueSettings s=settingsRepo.findById(1L).orElseGet(LeagueSettings::new);
        List<Integer> shifts=new ArrayList<>(); for(int i=0;i<8;i++) shifts.add(i);
        Collections.shuffle(shifts,new Random(s.getScheduleSeed()));
        for(int r=0;r<3;r++){
            int shift=shifts.get(r);
            int week=8+r;
            for(int i=0;i<8;i++){
                Team left=a.get(i), right=b.get((i+shift)%8);
                if((i+r)%2==0) matchups.save(new Matchup(week,left.getId(),right.getId()));
                else matchups.save(new Matchup(week,right.getId(),left.getId()));
            }
        }
    }

    private void addRoundRobin(List<Team> division,int startWeek){
        List<Team> ring=new ArrayList<>(division);
        int n=ring.size();
        for(int round=0;round<n-1;round++){
            int week=startWeek+round;
            for(int i=0;i<n/2;i++){
                Team x=ring.get(i), y=ring.get(n-1-i);
                if((round+i)%2==0) matchups.save(new Matchup(week,x.getId(),y.getId()));
                else matchups.save(new Matchup(week,y.getId(),x.getId()));
            }
            Team last=ring.remove(n-1);
            ring.add(1,last);
        }
    }

    @Transactional
    public void setResult(Long matchupId,int homeScore,int awayScore){
        Matchup m=matchups.findById(matchupId).orElseThrow();
        m.setHomeScore(homeScore);m.setAwayScore(awayScore);m.setPlayed(true);matchups.save(m);
        recalculateStandings();
    }

    @Transactional
    public void clearResult(Long matchupId){
        Matchup m=matchups.findById(matchupId).orElseThrow();
        m.setHomeScore(null);m.setAwayScore(null);m.setPlayed(false);matchups.save(m);
        recalculateStandings();
    }

    @Transactional
    public void recalculateStandings(){
        List<Team> all=teams.findAll();
        Map<Long,Team> map=new HashMap<>();
        for(Team t:all){t.setWins(0);t.setLosses(0);t.setDifferential(0);map.put(t.getId(),t);}
        for(Matchup m:matchups.findAll()){
            if(!m.isPlayed()||m.getHomeScore()==null||m.getAwayScore()==null) continue;
            Team h=map.get(m.getHomeTeamId()), a=map.get(m.getAwayTeamId());
            int diff=m.getHomeScore()-m.getAwayScore();
            h.setDifferential(h.getDifferential()+diff);a.setDifferential(a.getDifferential()-diff);
            if(diff>0){h.setWins(h.getWins()+1);a.setLosses(a.getLosses()+1);} else if(diff<0){a.setWins(a.getWins()+1);h.setLosses(h.getLosses()+1);}
        }
        teams.saveAll(all);
    }

    public List<Team> standings(String division){
        List<Team> list=teams.findByDivision(division);
        Map<String,Integer> h2h=headToHead();
        list.sort((x,y)->{
            if(x.getManualRank()>0 || y.getManualRank()>0){
                int xr=x.getManualRank()==0?999:x.getManualRank(), yr=y.getManualRank()==0?999:y.getManualRank();
                if(xr!=yr) return Integer.compare(xr,yr);
            }
            if(x.getWins()!=y.getWins()) return Integer.compare(y.getWins(),x.getWins());
            if(x.getDifferential()!=y.getDifferential()) return Integer.compare(y.getDifferential(),x.getDifferential());
            int xy=h2h.getOrDefault(key(x.getId(),y.getId()),0);
            if(xy!=0) return -Integer.compare(xy,0);
            return x.getName().compareToIgnoreCase(y.getName());
        });
        return list;
    }

    private Map<String,Integer> headToHead(){
        Map<String,Integer> h=new HashMap<>();
        for(Matchup m:matchups.findAll()){
            if(!m.isPlayed()||m.getHomeScore()==null||m.getAwayScore()==null||m.getHomeScore().equals(m.getAwayScore())) continue;
            Long winner=m.getHomeScore()>m.getAwayScore()?m.getHomeTeamId():m.getAwayTeamId();
            Long loser=winner.equals(m.getHomeTeamId())?m.getAwayTeamId():m.getHomeTeamId();
            h.put(key(winner,loser),1);h.put(key(loser,winner),-1);
        }
        return h;
    }
    private String key(Long a,Long b){return a+":"+b;}
}
