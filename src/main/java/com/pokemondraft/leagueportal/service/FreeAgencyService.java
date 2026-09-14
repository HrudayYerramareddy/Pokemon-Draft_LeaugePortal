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
    private final FreeAgentBidRepository bids; private final TeamRepository teams; private final PokemonRepository pokemon;
    private final RosterEntryRepository roster; private final TransactionRecordRepository transactions; private final TradeOfferRepository trades;
    public FreeAgencyService(FreeAgentBidRepository bids,TeamRepository teams,PokemonRepository pokemon,RosterEntryRepository roster,TransactionRecordRepository transactions,TradeOfferRepository trades){this.bids=bids;this.teams=teams;this.pokemon=pokemon;this.roster=roster;this.transactions=transactions;this.trades=trades;}

    public FreeAgentBid submitBid(Long teamId,Long wanted,Long drop,int amount){
        Team t=teams.findById(teamId).orElseThrow();
        if(amount<0||amount>t.getFaBudget()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Bid exceeds budget");
        if(roster.findByPokemonId(wanted).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Wanted Pokemon is not a free agent");
        if(drop!=null){RosterEntry d=roster.findByPokemonId(drop).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,"Drop Pokemon is not rostered")); if(!d.getTeamId().equals(teamId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Not your Pokemon");}
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
                Team t=teams.findById(candidate.getTeamId()).orElseThrow();
                if(candidate.getAmount()<=t.getFaBudget() && roster.findByPokemonId(candidate.getWantedPokemonId()).isEmpty()){winner=candidate;break;}
            }
            for(FreeAgentBid b:group) b.setStatus(b==winner?BidStatus.WON:BidStatus.LOST);
            if(winner!=null){applyWinningBid(winner);winners++;}
            bids.saveAll(group);
        }
        return winners;
    }

    private void applyWinningBid(FreeAgentBid b){
        Team t=teams.findById(b.getTeamId()).orElseThrow(); Pokemon wanted=pokemon.findById(b.getWantedPokemonId()).orElseThrow();
        if(b.getDropPokemonId()!=null){
            roster.findByPokemonId(b.getDropPokemonId()).ifPresent(e->{roster.delete(e);pokemon.findById(b.getDropPokemonId()).ifPresent(dp->{dp.setDrafted(false);pokemon.save(dp);});});
        } else if(roster.countByTeamId(t.getId())>=10) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Winning team has no roster space");
        roster.save(new RosterEntry(t.getId(),wanted.getId())); wanted.setDrafted(true);pokemon.save(wanted);
        t.setFaBudget(t.getFaBudget()-b.getAmount());teams.save(t);
        String drop=b.getDropPokemonId()==null?"no drop":pokemon.findById(b.getDropPokemonId()).map(Pokemon::getName).orElse("unknown");
        transactions.save(new TransactionRecord("FREE_AGENCY",t.getName()+" added "+wanted.getName()+" for $"+b.getAmount()+" and dropped "+drop));
    }

    public TradeOffer offerTrade(Long teamId,Long recipient,Long offered,Long requested){
        if(teamId.equals(recipient)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cannot trade with yourself");
        verifyOwner(teamId,offered);verifyOwner(recipient,requested);
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
        offered.setTeamId(tr.getRecipientTeamId());requested.setTeamId(tr.getProposerTeamId());roster.save(offered);roster.save(requested);
        tr.setStatus(TradeStatus.ACCEPTED);trades.save(tr);
        Team p=teams.findById(tr.getProposerTeamId()).orElseThrow(), r=teams.findById(tr.getRecipientTeamId()).orElseThrow();
        String a=pokemon.findById(tr.getOfferedPokemonId()).map(Pokemon::getName).orElse("Pokemon"), b=pokemon.findById(tr.getRequestedPokemonId()).map(Pokemon::getName).orElse("Pokemon");
        transactions.save(new TransactionRecord("TRADE",p.getName()+" traded "+a+" to "+r.getName()+" for "+b));
        return tr;
    }
    private RosterEntry verifyOwner(Long teamId,Long pokemonId){
        RosterEntry e=roster.findByPokemonId(pokemonId).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,"Pokemon is not rostered"));
        if(!e.getTeamId().equals(teamId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Pokemon belongs to another team");
        return e;
    }
}
