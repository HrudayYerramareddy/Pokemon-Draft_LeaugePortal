package com.pokemondraft.leagueportal.service;

import com.pokemondraft.leagueportal.model.*;
import com.pokemondraft.leagueportal.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class FreeAgencyService {
    private static final int ROSTER_LIMIT=10, ROSTER_BUDGET=100;
    private final FreeAgentBidRepository bids; private final TeamRepository teams; private final PokemonRepository pokemon;
    private final RosterEntryRepository roster; private final TransactionRecordRepository transactions; private final TradeOfferRepository trades;
    public FreeAgencyService(FreeAgentBidRepository bids,TeamRepository teams,PokemonRepository pokemon,RosterEntryRepository roster,TransactionRecordRepository transactions,TradeOfferRepository trades){this.bids=bids;this.teams=teams;this.pokemon=pokemon;this.roster=roster;this.transactions=transactions;this.trades=trades;}

    public FreeAgentBid submitBid(Long teamId,Long wanted,Long drop,int amount){
        Team t=teams.findById(teamId).orElseThrow();
        if(t.getFaBudget()<=0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"You have no FAAB remaining");
        if(amount<=0||amount>t.getFaBudget())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Bid must be between $1 and your remaining FAAB");
        if(wanted==null||drop==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a free agent and one Pokemon to drop");
        if(roster.countByTeamId(teamId)!=ROSTER_LIMIT)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Free agency requires a full 10-Pokemon roster");
        if(roster.findByPokemonId(wanted).isPresent())throw new ResponseStatusException(HttpStatus.CONFLICT,"Wanted Pokemon is not a free agent");
        RosterEntry d=verifyOwner(teamId,drop); Pokemon w=pokemon.findById(wanted).orElseThrow(), dp=pokemon.findById(d.getPokemonId()).orElseThrow();
        int value=rosterValue(teamId)-dp.getPrice()+w.getPrice(); if(value>ROSTER_BUDGET)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Move would put roster value at $"+value+"/$100");
        return bids.save(new FreeAgentBid(teamId,wanted,drop,amount));
    }

    @Transactional public int processBids(){
        List<FreeAgentBid> pending=bids.findByStatus(BidStatus.PENDING); Map<Long,List<FreeAgentBid>> groups=new HashMap<>();
        for(FreeAgentBid b:pending)groups.computeIfAbsent(b.getWantedPokemonId(),k->new ArrayList<>()).add(b); int winners=0;
        for(List<FreeAgentBid> g:groups.values()){
            g.sort(Comparator.comparingInt(FreeAgentBid::getAmount).reversed().thenComparing(FreeAgentBid::getCreatedAt)); FreeAgentBid winner=null;
            for(FreeAgentBid b:g)if(canApplyBid(b)){winner=b;break;}
            for(FreeAgentBid b:g)b.setStatus(b==winner?BidStatus.WON:BidStatus.LOST); if(winner!=null){applyWinningBid(winner);winners++;} bids.saveAll(g);
        } return winners;
    }
    private boolean canApplyBid(FreeAgentBid b){
        Team t=teams.findById(b.getTeamId()).orElse(null); if(t==null||b.getAmount()<=0||b.getAmount()>t.getFaBudget()||b.getDropPokemonId()==null||roster.countByTeamId(b.getTeamId())!=ROSTER_LIMIT||roster.findByPokemonId(b.getWantedPokemonId()).isPresent())return false;
        Optional<RosterEntry>d=roster.findByPokemonId(b.getDropPokemonId()); if(d.isEmpty()||!d.get().getTeamId().equals(b.getTeamId()))return false;
        Pokemon w=pokemon.findById(b.getWantedPokemonId()).orElse(null),dp=pokemon.findById(b.getDropPokemonId()).orElse(null); return w!=null&&dp!=null&&rosterValue(b.getTeamId())-dp.getPrice()+w.getPrice()<=ROSTER_BUDGET;
    }
    private void applyWinningBid(FreeAgentBid b){
        Team t=teams.findById(b.getTeamId()).orElseThrow(); Pokemon w=pokemon.findById(b.getWantedPokemonId()).orElseThrow(),dp=pokemon.findById(b.getDropPokemonId()).orElseThrow(); RosterEntry de=verifyOwner(t.getId(),dp.getId());
        int value=rosterValue(t.getId())-dp.getPrice()+w.getPrice(); if(value>ROSTER_BUDGET)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Winning bid exceeds $100 roster budget");
        roster.delete(de);dp.setDrafted(false);pokemon.save(dp);roster.save(new RosterEntry(t.getId(),w.getId()));w.setDrafted(true);pokemon.save(w);t.setFaBudget(t.getFaBudget()-b.getAmount());teams.save(t);
        transactions.save(new TransactionRecord("FREE_AGENCY",t.getName()+" added "+w.getName()+" for $"+b.getAmount()+" FAAB and dropped "+dp.getName()+". Roster value: $"+value+"/$100; FAAB remaining: $"+t.getFaBudget()));
    }

    public TradeOffer offerTrade(Long teamId,Long recipient,Long offered,Long requested,int proposerFaab,int recipientFaab){
        if(recipient==null||offered==null||requested==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a team and one Pokemon from each roster");
        if(teamId.equals(recipient))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cannot trade with yourself"); if(proposerFaab<0||recipientFaab<0||(proposerFaab>0&&recipientFaab>0))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"FAAB can only move one direction in a trade");
        Team p=teams.findById(teamId).orElseThrow(),r=teams.findById(recipient).orElseThrow(); if(proposerFaab>p.getFaBudget()||recipientFaab>r.getFaBudget())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Trade includes more FAAB than a team owns");
        verifyOwner(teamId,offered);verifyOwner(recipient,requested);validateTradeBudgets(teamId,recipient,offered,requested);return trades.save(new TradeOffer(teamId,recipient,offered,requested,proposerFaab,recipientFaab));
    }

    @Transactional public TradeOffer respondTrade(Long id,Long actingTeamId,boolean manager,boolean accept){
        TradeOffer tr=trades.findById(id).orElseThrow(); if(tr.getStatus()!=TradeStatus.PENDING)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Trade already resolved"); if(!manager&&!tr.getRecipientTeamId().equals(actingTeamId))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only recipient can respond");
        if(!accept){tr.setStatus(TradeStatus.REJECTED);return trades.save(tr);} RosterEntry offered=verifyOwner(tr.getProposerTeamId(),tr.getOfferedPokemonId()),requested=verifyOwner(tr.getRecipientTeamId(),tr.getRequestedPokemonId()); int[] values=validateTradeBudgets(tr.getProposerTeamId(),tr.getRecipientTeamId(),tr.getOfferedPokemonId(),tr.getRequestedPokemonId());
        Team p=teams.findById(tr.getProposerTeamId()).orElseThrow(),r=teams.findById(tr.getRecipientTeamId()).orElseThrow(); if(tr.getProposerFaab()>p.getFaBudget()||tr.getRecipientFaab()>r.getFaBudget())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A team no longer has the FAAB included in this offer");
        offered.setTeamId(r.getId());requested.setTeamId(p.getId());roster.save(offered);roster.save(requested);p.setFaBudget(p.getFaBudget()-tr.getProposerFaab()+tr.getRecipientFaab());r.setFaBudget(r.getFaBudget()-tr.getRecipientFaab()+tr.getProposerFaab());teams.save(p);teams.save(r);tr.setStatus(TradeStatus.ACCEPTED);trades.save(tr);
        String a=pokemon.findById(tr.getOfferedPokemonId()).map(Pokemon::getName).orElse("Pokemon"),b=pokemon.findById(tr.getRequestedPokemonId()).map(Pokemon::getName).orElse("Pokemon"); String faab=tr.getProposerFaab()>0?" + $"+tr.getProposerFaab()+" FAAB":tr.getRecipientFaab()>0?" for "+b+" + $"+tr.getRecipientFaab()+" FAAB":" for "+b;
        String desc=tr.getRecipientFaab()>0?p.getName()+" traded "+a+" to "+r.getName()+faab:p.getName()+" traded "+a+(tr.getProposerFaab()>0?" + $"+tr.getProposerFaab()+" FAAB":"")+" to "+r.getName()+" for "+b;
        transactions.save(new TransactionRecord("TRADE",desc+". Roster values: $"+values[0]+"/$100 and $"+values[1]+"/$100. FAAB: $"+p.getFaBudget()+" / $"+r.getFaBudget())); return tr;
    }
    private int[] validateTradeBudgets(Long p,Long r,Long a,Long b){Pokemon x=pokemon.findById(a).orElseThrow(),y=pokemon.findById(b).orElseThrow();int pv=rosterValue(p)-x.getPrice()+y.getPrice(),rv=rosterValue(r)-y.getPrice()+x.getPrice();if(pv>100||rv>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Trade would exceed the $100 roster budget");return new int[]{pv,rv};}
    private int rosterValue(Long teamId){int n=0;for(RosterEntry e:roster.findByTeamId(teamId))n+=pokemon.findById(e.getPokemonId()).map(Pokemon::getPrice).orElse(0);return n;}
    private RosterEntry verifyOwner(Long teamId,Long pokemonId){RosterEntry e=roster.findByPokemonId(pokemonId).orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Pokemon is not rostered"));if(!e.getTeamId().equals(teamId))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Pokemon belongs to another team");return e;}
}
