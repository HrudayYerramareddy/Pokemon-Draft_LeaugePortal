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
    private static final int ROSTER_LIMIT = 10;
    private static final int ROSTER_BUDGET = 100;

    private final FreeAgentBidRepository bids; private final TeamRepository teams; private final PokemonRepository pokemon;
    private final RosterEntryRepository roster; private final TransactionRecordRepository transactions; private final TradeOfferRepository trades;
    public FreeAgencyService(FreeAgentBidRepository bids,TeamRepository teams,PokemonRepository pokemon,RosterEntryRepository roster,TransactionRecordRepository transactions,TradeOfferRepository trades){this.bids=bids;this.teams=teams;this.pokemon=pokemon;this.roster=roster;this.transactions=transactions;this.trades=trades;}

    public FreeAgentBid submitBid(Long teamId,Long wanted,Long drop,int amount){
        Team t=teams.findById(teamId).orElseThrow();
        if(amount<0||amount>t.getFaBudget()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Bid exceeds FAAB budget");
        if(wanted==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a free agent");
        if(drop==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose one Pokemon to drop");
        if(wanted.equals(drop)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Add and drop Pokemon must be different");
        if(roster.findByPokemonId(wanted).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Wanted Pokemon is not a free agent");
        RosterEntry d=verifyOwner(teamId,drop);
        Pokemon wantedPokemon=pokemon.findById(wanted).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,"Free agent not found"));
        Pokemon dropPokemon=pokemon.findById(d.getPokemonId()).orElseThrow();
        int newValue=rosterValue(teamId)-dropPokemon.getPrice()+wantedPokemon.getPrice();
        if(newValue>ROSTER_BUDGET) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Move would put roster value at $"+newValue+". Maximum is $100");
        if(roster.countByTeamId(teamId)>ROSTER_LIMIT) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Roster exceeds 10 Pokemon");
        return bids.save(new FreeAgentBid(teamId,wanted,drop,amount));
    }

    @Transactional
    public int processBids(){
        List<FreeAgentBid> pending=bids.findByStatus(BidStatus.PENDING);
        Map<Long,List<FreeAgentBid>> byPokemon=new HashMap<>();
        for(FreeAgentBid b:pending) byPokemon.computeIfAbsent(b.getWantedPokemonId(),k->new ArrayList<>()).add(b);
        int winners=0;
        for(List<FreeAgentBid> group:byPokemon.values()){
            group.sort(Comparator.comparingInt(FreeAgentBid::getAmount).reversed().thenComparing(FreeAgentBid::getCreatedAt));
            FreeAgentBid winner=null;
            for(FreeAgentBid candidate:group){
                if(canApplyBid(candidate)){winner=candidate;break;}
            }
            for(FreeAgentBid b:group) b.setStatus(b==winner?BidStatus.WON:BidStatus.LOST);
            if(winner!=null){applyWinningBid(winner);winners++;}
            bids.saveAll(group);
        }
        return winners;
    }

    private boolean canApplyBid(FreeAgentBid b){
        Team t=teams.findById(b.getTeamId()).orElse(null);
        if(t==null||b.getDropPokemonId()==null||b.getAmount()>t.getFaBudget()||roster.findByPokemonId(b.getWantedPokemonId()).isPresent()) return false;
        Optional<RosterEntry> dropEntry=roster.findByPokemonId(b.getDropPokemonId());
        if(dropEntry.isEmpty()||!dropEntry.get().getTeamId().equals(b.getTeamId())) return false;
        Pokemon wanted=pokemon.findById(b.getWantedPokemonId()).orElse(null), drop=pokemon.findById(b.getDropPokemonId()).orElse(null);
        if(wanted==null||drop==null) return false;
        return rosterValue(b.getTeamId())-drop.getPrice()+wanted.getPrice()<=ROSTER_BUDGET;
    }

    private void applyWinningBid(FreeAgentBid b){
        Team t=teams.findById(b.getTeamId()).orElseThrow(); Pokemon wanted=pokemon.findById(b.getWantedPokemonId()).orElseThrow();
        RosterEntry dropEntry=verifyOwner(t.getId(),b.getDropPokemonId());
        Pokemon dropped=pokemon.findById(b.getDropPokemonId()).orElseThrow();
        int newValue=rosterValue(t.getId())-dropped.getPrice()+wanted.getPrice();
        if(newValue>ROSTER_BUDGET) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Winning bid would exceed the $100 roster budget");
        roster.delete(dropEntry); dropped.setDrafted(false); pokemon.save(dropped);
        roster.save(new RosterEntry(t.getId(),wanted.getId())); wanted.setDrafted(true);pokemon.save(wanted);
        t.setFaBudget(t.getFaBudget()-b.getAmount());teams.save(t);
        transactions.save(new TransactionRecord("FREE_AGENCY",t.getName()+" added "+wanted.getName()+" for $"+b.getAmount()+" FAAB and dropped "+dropped.getName()+". Roster value: $"+newValue+"/$100"));
    }

    public TradeOffer offerTrade(Long teamId,Long recipient,Long offered,Long requested){
        if(recipient==null||offered==null||requested==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a team and one Pokemon from each roster");
        if(teamId.equals(recipient)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cannot trade with yourself");
        teams.findById(recipient).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,"Target team not found"));
        verifyOwner(teamId,offered);verifyOwner(recipient,requested);
        validateTradeBudgets(teamId,recipient,offered,requested);
        return trades.save(new TradeOffer(teamId,recipient,offered,requested));
    }

    @Transactional
    public TradeOffer respondTrade(Long tradeId,Long actingTeamId,boolean manager,boolean accept){
        TradeOffer tr=trades.findById(tradeId).orElseThrow();
        if(tr.getStatus()!=TradeStatus.PENDING) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Trade already resolved");
        if(!manager && !tr.getRecipientTeamId().equals(actingTeamId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only recipient can respond");
        if(!accept){tr.setStatus(TradeStatus.REJECTED);return trades.save(tr);}
        RosterEntry offered=verifyOwner(tr.getProposerTeamId(),tr.getOfferedPokemonId());
        RosterEntry requested=verifyOwner(tr.getRecipientTeamId(),tr.getRequestedPokemonId());
        int[] values=validateTradeBudgets(tr.getProposerTeamId(),tr.getRecipientTeamId(),tr.getOfferedPokemonId(),tr.getRequestedPokemonId());
        offered.setTeamId(tr.getRecipientTeamId());requested.setTeamId(tr.getProposerTeamId());roster.save(offered);roster.save(requested);
        tr.setStatus(TradeStatus.ACCEPTED);trades.save(tr);
        Team p=teams.findById(tr.getProposerTeamId()).orElseThrow(), r=teams.findById(tr.getRecipientTeamId()).orElseThrow();
        String a=pokemon.findById(tr.getOfferedPokemonId()).map(Pokemon::getName).orElse("Pokemon"), b=pokemon.findById(tr.getRequestedPokemonId()).map(Pokemon::getName).orElse("Pokemon");
        transactions.save(new TransactionRecord("TRADE",p.getName()+" traded "+a+" to "+r.getName()+" for "+b+". New roster values: "+p.getName()+" $"+values[0]+"/$100, "+r.getName()+" $"+values[1]+"/$100"));
        return tr;
    }

    private int[] validateTradeBudgets(Long proposer,Long recipient,Long offeredId,Long requestedId){
        Pokemon offered=pokemon.findById(offeredId).orElseThrow(), requested=pokemon.findById(requestedId).orElseThrow();
        int proposerValue=rosterValue(proposer)-offered.getPrice()+requested.getPrice();
        int recipientValue=rosterValue(recipient)-requested.getPrice()+offered.getPrice();
        if(proposerValue>ROSTER_BUDGET||recipientValue>ROSTER_BUDGET)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Trade would exceed the $100 roster budget (proposer $"+proposerValue+", recipient $"+recipientValue+")");
        if(roster.countByTeamId(proposer)>ROSTER_LIMIT||roster.countByTeamId(recipient)>ROSTER_LIMIT)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A team roster exceeds the 10 Pokemon limit");
        return new int[]{proposerValue,recipientValue};
    }

    private int rosterValue(Long teamId){
        int total=0;
        for(RosterEntry e:roster.findByTeamId(teamId)) total+=pokemon.findById(e.getPokemonId()).map(Pokemon::getPrice).orElse(0);
        return total;
    }

    private RosterEntry verifyOwner(Long teamId,Long pokemonId){
        RosterEntry e=roster.findByPokemonId(pokemonId).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,"Pokemon is not rostered"));
        if(!e.getTeamId().equals(teamId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Pokemon belongs to another team");
        return e;
    }
}
