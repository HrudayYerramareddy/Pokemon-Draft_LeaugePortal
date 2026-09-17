package com.pokemondraft.leagueportal.service;

import com.pokemondraft.leagueportal.model.*;
import com.pokemondraft.leagueportal.repository.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FreeAgencyService {
  private static final int ROSTER_LIMIT = 10, ROSTER_BUDGET = 100;
  private final FreeAgentBidRepository bids;
  private final TeamRepository teams;
  private final PokemonRepository pokemon;
  private final RosterEntryRepository roster;
  private final TransactionRecordRepository transactions;
  private final TradeOfferRepository trades;

  public FreeAgencyService(
      FreeAgentBidRepository bids,
      TeamRepository teams,
      PokemonRepository pokemon,
      RosterEntryRepository roster,
      TransactionRecordRepository transactions,
      TradeOfferRepository trades) {
    this.bids = bids;
    this.teams = teams;
    this.pokemon = pokemon;
    this.roster = roster;
    this.transactions = transactions;
    this.trades = trades;
  }

  public FreeAgentBid submitBid(Long teamId, Long wanted, Long drop, int amount) {
    Team t = teams.findById(teamId).orElseThrow();
    if (t.getFaBudget() <= 0) throw bad("You have no FAAB remaining");
    if (amount <= 0 || amount > t.getFaBudget())
      throw bad("Bid must be between $1 and your remaining FAAB");
    if (wanted == null) throw bad("Choose a free agent");
    Pokemon w = pokemon.findById(wanted).orElseThrow(() -> bad("Free agent does not exist"));
    if (roster.findByPokemonId(wanted).isPresent() || w.isDrafted())
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Wanted Pokemon is not a free agent");
    int count = (int) roster.countByTeamId(teamId);
    if (count != ROSTER_LIMIT) throw bad("Free agency requires a complete 10-Pokemon roster");
    if (drop == null)
      throw bad("Choose one Pokemon to drop. Rosters must stay at exactly 10 Pokemon");
    int value = rosterValue(teamId) + w.getPrice();
    RosterEntry d = verifyOwner(teamId, drop);
    Pokemon dp = pokemon.findById(d.getPokemonId()).orElseThrow();
    value -= dp.getPrice();
    if (value > ROSTER_BUDGET) throw bad("Move would put roster value at $" + value + "/$100");
    return bids.save(new FreeAgentBid(teamId, wanted, drop, amount));
  }

  @Transactional
  public int processBids() {
    List<FreeAgentBid> pending = bids.findByStatus(BidStatus.PENDING);
    Map<Long, List<FreeAgentBid>> groups = new LinkedHashMap<>();
    for (FreeAgentBid b : pending)
      groups.computeIfAbsent(b.getWantedPokemonId(), k -> new ArrayList<>()).add(b);
    int winners = 0;
    for (List<FreeAgentBid> g : groups.values()) {
      g.sort(
          Comparator.comparingInt(FreeAgentBid::getAmount)
              .reversed()
              .thenComparing(FreeAgentBid::getCreatedAt));
      FreeAgentBid winner = null;
      for (FreeAgentBid b : g)
        if (canApplyBid(b)) {
          winner = b;
          break;
        }
      for (FreeAgentBid b : g) b.setStatus(b == winner ? BidStatus.WON : BidStatus.LOST);
      if (winner != null) {
        applyWinningBid(winner);
        winners++;
      }
      bids.saveAll(g);
    }
    return winners;
  }

  private boolean canApplyBid(FreeAgentBid b) {
    Team t = teams.findById(b.getTeamId()).orElse(null);
    Pokemon w = pokemon.findById(b.getWantedPokemonId()).orElse(null);
    if (t == null
        || w == null
        || b.getAmount() <= 0
        || b.getAmount() > t.getFaBudget()
        || roster.findByPokemonId(b.getWantedPokemonId()).isPresent()
        || w.isDrafted()) return false;
    if (roster.countByTeamId(b.getTeamId()) != ROSTER_LIMIT || b.getDropPokemonId() == null)
      return false;
    Optional<RosterEntry> d = roster.findByPokemonId(b.getDropPokemonId());
    if (d.isEmpty() || !d.get().getTeamId().equals(b.getTeamId())) return false;
    Pokemon dp = pokemon.findById(b.getDropPokemonId()).orElse(null);
    return dp != null && rosterValue(b.getTeamId()) - dp.getPrice() + w.getPrice() <= ROSTER_BUDGET;
  }

  private void applyWinningBid(FreeAgentBid b) {
    Team t = teams.findById(b.getTeamId()).orElseThrow();
    Pokemon w = pokemon.findById(b.getWantedPokemonId()).orElseThrow();
    Pokemon dp = pokemon.findById(b.getDropPokemonId()).orElseThrow();
    RosterEntry de = verifyOwner(t.getId(), dp.getId());
    int value = rosterValue(t.getId()) - dp.getPrice() + w.getPrice();
    if (value > ROSTER_BUDGET) throw bad("Winning bid exceeds $100 roster budget");
    roster.delete(de);
    dp.setDrafted(false);
    pokemon.save(dp);
    roster.save(new RosterEntry(t.getId(), w.getId()));
    w.setDrafted(true);
    pokemon.save(w);
    t.setFaBudget(t.getFaBudget() - b.getAmount());
    teams.save(t);
    transactions.save(
        new TransactionRecord(
            "FREE_AGENCY",
            t.getName()
                + " added "
                + w.getName()
                + " for $"
                + b.getAmount()
                + " FAAB and dropped "
                + dp.getName()
                + ". Roster value: $"
                + value
                + "/$100; FAAB remaining: $"
                + t.getFaBudget()));
    cancelImpossibleTrades(Set.of(w.getId(), dp.getId()));
  }

  public TradeOffer offerTrade(
      Long teamId,
      Long recipient,
      List<Long> offered,
      List<Long> requested,
      int proposerFaab,
      int recipientFaab) {
    if (recipient == null) throw bad("Choose a team to trade with");
    if (teamId.equals(recipient)) throw bad("You cannot trade with your own team");
    offered = clean(offered);
    requested = clean(requested);
    if (offered.isEmpty() && requested.isEmpty() && proposerFaab == 0 && recipientFaab == 0)
      throw bad("Trade must include at least one Pokemon or FAAB");
    if (proposerFaab < 0 || recipientFaab < 0) throw bad("FAAB cannot be negative");
    Team p = teams.findById(teamId).orElseThrow(), r = teams.findById(recipient).orElseThrow();
    if (proposerFaab > p.getFaBudget())
      throw bad(
          "You only have $" + p.getFaBudget() + " FAAB, so you cannot offer $" + proposerFaab);
    if (recipientFaab > r.getFaBudget())
      throw bad(
          r.getName()
              + " only has $"
              + r.getFaBudget()
              + " FAAB, so you cannot request $"
              + recipientFaab);
    for (Long id : offered) verifyOwner(teamId, id);
    for (Long id : requested) verifyOwner(recipient, id);
    validateTrade(teamId, recipient, offered, requested);
    return trades.save(
        new TradeOffer(teamId, recipient, offered, requested, proposerFaab, recipientFaab));
  }

  public TradeOffer offerTrade(
      Long teamId, Long recipient, Long offered, Long requested, int pf, int rf) {
    return offerTrade(
        teamId,
        recipient,
        offered == null ? List.of() : List.of(offered),
        requested == null ? List.of() : List.of(requested),
        pf,
        rf);
  }

  public TradeOffer offerTrade(Long teamId, Long recipient, Long offered, Long requested) {
    return offerTrade(teamId, recipient, offered, requested, 0, 0);
  }

  @Transactional
  public TradeOffer respondTrade(Long id, Long actingTeamId, boolean manager, boolean accept) {
    TradeOffer tr = trades.findById(id).orElseThrow(() -> bad("Trade offer no longer exists"));
    if (tr.getStatus() != TradeStatus.PENDING)
      throw bad(
          "This trade is already "
              + tr.getStatus().name().toLowerCase()
              + " and can no longer be changed");
    if (!manager && !tr.getRecipientTeamId().equals(actingTeamId))
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only the team receiving this trade can accept or reject it");
    if (!accept) {
      tr.setStatus(TradeStatus.REJECTED);
      return trades.save(tr);
    }
    List<Long> offered = tr.getOfferedPokemonIds(), requested = tr.getRequestedPokemonIds();
    if (!assetsStillOwned(tr.getProposerTeamId(), offered)
        || !assetsStillOwned(tr.getRecipientTeamId(), requested)) {
      tr.setStatus(TradeStatus.CANCELLED);
      return trades.save(tr);
    }
    int[] values =
        validateTrade(tr.getProposerTeamId(), tr.getRecipientTeamId(), offered, requested);
    Team p = teams.findById(tr.getProposerTeamId()).orElseThrow(),
        r = teams.findById(tr.getRecipientTeamId()).orElseThrow();
    if (tr.getProposerFaab() > p.getFaBudget() || tr.getRecipientFaab() > r.getFaBudget()) {
      tr.setStatus(TradeStatus.CANCELLED);
      return trades.save(tr);
    }
    for (Long x : offered) {
      RosterEntry e = verifyOwner(p.getId(), x);
      e.setTeamId(r.getId());
      roster.save(e);
    }
    for (Long x : requested) {
      RosterEntry e = verifyOwner(r.getId(), x);
      e.setTeamId(p.getId());
      roster.save(e);
    }
    p.setFaBudget(p.getFaBudget() - tr.getProposerFaab() + tr.getRecipientFaab());
    r.setFaBudget(r.getFaBudget() - tr.getRecipientFaab() + tr.getProposerFaab());
    teams.save(p);
    teams.save(r);
    tr.setStatus(TradeStatus.ACCEPTED);
    trades.save(tr);
    Set<Long> moved = new HashSet<>(offered);
    moved.addAll(requested);
    cancelImpossibleTradesExcept(moved, tr.getId());
    String desc =
        p.getName()
            + " traded "
            + describe(offered, tr.getProposerFaab())
            + " to "
            + r.getName()
            + " for "
            + describe(requested, tr.getRecipientFaab());
    transactions.save(
        new TransactionRecord(
            "TRADE",
            desc
                + ". Roster values: $"
                + values[0]
                + "/$100 and $"
                + values[1]
                + "/$100. FAAB remaining: $"
                + p.getFaBudget()
                + " / $"
                + r.getFaBudget()));
    return tr;
  }

  private int[] validateTrade(Long p, Long r, List<Long> offered, List<Long> requested) {
    Team pt = teams.findById(p).orElseThrow(), rt = teams.findById(r).orElseThrow();
    int pc = (int) roster.countByTeamId(p) - offered.size() + requested.size(),
        rc = (int) roster.countByTeamId(r) - requested.size() + offered.size();
    if (pc != ROSTER_LIMIT)
      throw bad(
          "This trade would leave "
              + pt.getName()
              + " with "
              + pc
              + " Pokemon. Every roster must have exactly 10");
    if (rc != ROSTER_LIMIT)
      throw bad(
          "This trade would leave "
              + rt.getName()
              + " with "
              + rc
              + " Pokemon. Every roster must have exactly 10");
    int pv = rosterValue(p) - value(offered) + value(requested),
        rv = rosterValue(r) - value(requested) + value(offered);
    if (pv > ROSTER_BUDGET)
      throw bad(
          "This trade would put "
              + pt.getName()
              + " over the $100 roster budget at $"
              + pv
              + "/$100");
    if (rv > ROSTER_BUDGET)
      throw bad(
          "This trade would put "
              + rt.getName()
              + " over the $100 roster budget at $"
              + rv
              + "/$100");
    return new int[] {pv, rv};
  }

  @Transactional
  public void cancelImpossibleTrades(Set<Long> moved) {
    cancelImpossibleTradesExcept(moved, null);
  }

  private void cancelImpossibleTradesExcept(Set<Long> moved, Long acceptedId) {
    for (TradeOffer t : trades.findAll()) {
      if (t.getStatus() != TradeStatus.PENDING || Objects.equals(t.getId(), acceptedId)) continue;
      boolean conflict =
          t.getOfferedPokemonIds().stream().anyMatch(moved::contains)
              || t.getRequestedPokemonIds().stream().anyMatch(moved::contains);
      if (conflict) {
        t.setStatus(TradeStatus.CANCELLED);
        trades.save(t);
      }
    }
  }

  private boolean assetsStillOwned(Long teamId, List<Long> ids) {
    for (Long id : ids) {
      Optional<RosterEntry> e = roster.findByPokemonId(id);
      if (e.isEmpty() || !e.get().getTeamId().equals(teamId)) return false;
    }
    return true;
  }

  private List<Long> clean(List<Long> ids) {
    return ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
  }

  private int value(List<Long> ids) {
    int v = 0;
    for (Long id : ids) v += pokemon.findById(id).map(Pokemon::getPrice).orElse(0);
    return v;
  }

  private String describe(List<Long> ids, int faab) {
    List<String> parts = new ArrayList<>();
    for (Long id : ids) parts.add(pokemon.findById(id).map(Pokemon::getName).orElse("Pokemon"));
    if (faab > 0) parts.add("$" + faab + " FAAB");
    return parts.isEmpty() ? "nothing" : String.join(" + ", parts);
  }

  private int rosterValue(Long teamId) {
    int n = 0;
    for (RosterEntry e : roster.findByTeamId(teamId))
      n += pokemon.findById(e.getPokemonId()).map(Pokemon::getPrice).orElse(0);
    return n;
  }

  private RosterEntry verifyOwner(Long teamId, Long pokemonId) {
    RosterEntry e =
        roster
            .findByPokemonId(pokemonId)
            .orElseThrow(() -> bad(pokemonName(pokemonId) + " is not currently rostered"));
    if (!e.getTeamId().equals(teamId))
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, pokemonName(pokemonId) + " belongs to another team");
    return e;
  }

  private String pokemonName(Long id) {
    return pokemon.findById(id).map(Pokemon::getName).orElse("That Pokemon");
  }

  private ResponseStatusException bad(String s) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, s);
  }
}
