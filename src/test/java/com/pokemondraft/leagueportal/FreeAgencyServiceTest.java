package com.pokemondraft.leagueportal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.pokemondraft.leagueportal.entity.*;
import com.pokemondraft.leagueportal.repositories.*;
import com.pokemondraft.leagueportal.services.FreeAgencyService;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class FreeAgencyServiceTest {
  FreeAgentBidRepository bids = mock(FreeAgentBidRepository.class);
  TeamRepository teams = mock(TeamRepository.class);
  PokemonRepository pokemon = mock(PokemonRepository.class);
  RosterEntryRepository roster = mock(RosterEntryRepository.class);
  TransactionRecordRepository transactions = mock(TransactionRecordRepository.class);
  TradeOfferRepository trades = mock(TradeOfferRepository.class);
  MatchupRepository matchups = mock(MatchupRepository.class);
  LeagueSettingsRepository settings = mock(LeagueSettingsRepository.class);
  FreeAgencyService service;
  LeagueSettings config;
  Team team;
  List<TransactionRecord> journal = new ArrayList<>();

  @BeforeEach void setup() {
    service = new FreeAgencyService(bids, teams, pokemon, roster, transactions, trades, matchups, settings);
    config = new LeagueSettings();
    config.setCurrentWeek(4);
    when(settings.findById(1L)).thenReturn(Optional.of(config));
    Matchup third = new Matchup(3, 1L, 2L);
    third.setPlayed(true);
    when(matchups.findByWeekOrderByIdAsc(3)).thenReturn(Collections.nCopies(8, third));
    when(transactions.findAllByOrderByCreatedAtDesc()).thenAnswer(inv -> List.copyOf(journal));
    when(transactions.save(any(TransactionRecord.class))).thenAnswer(inv -> {
      TransactionRecord record = inv.getArgument(0);
      journal.add(record);
      return record;
    });
    team = new Team("Team 1", "Coach", "A", 1);
    team.setFaBudget(3);
    when(teams.findById(1L)).thenReturn(Optional.of(team));
    when(bids.findByStatus(BidStatus.PENDING)).thenReturn(List.of());
    List<RosterEntry> entries = new ArrayList<>();
    for (long id = 10; id < 20; id++) {
      entries.add(new RosterEntry(1L, id));
      when(pokemon.findById(id)).thenReturn(Optional.of(new Pokemon("Roster " + id, 5)));
    }
    when(roster.countByTeamId(1L)).thenReturn(10L);
    when(roster.findByTeamId(1L)).thenReturn(entries);
    when(roster.findByPokemonId(10L)).thenReturn(Optional.of(entries.get(0)));
    when(pokemon.findById(99L)).thenReturn(Optional.of(new Pokemon("Available", 4)));
    when(pokemon.findById(98L)).thenReturn(Optional.of(new Pokemon("Expensive", 60)));
    when(bids.save(any(FreeAgentBid.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test void windowRequiresCompletedWeekThreeAndCurrentWeekFour() {
    assertTrue(service.isWaiverWindowOpen());
    config.setCurrentWeek(3);
    assertFalse(service.isWaiverWindowOpen());
    config.setCurrentWeek(5);
    assertFalse(service.isWaiverWindowOpen());
    config.setCurrentWeek(4);
    assertTrue(service.isWaiverWindowOpen());
    config.setWaiverProcessed(true);
    assertFalse(service.isWaiverWindowOpen());
  }

  @Test void singleBidMustBeOneToThreeDollars() {
    assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
        () -> service.submitBid(1L, 99L, 10L, 0)).getStatusCode());
    assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
        () -> service.submitBid(1L, 99L, 10L, 4)).getStatusCode());
    assertEquals(3, service.submitBid(1L, 99L, 10L, 3).getAmount());
  }

  @Test void combinedPendingBidsCannotExceedThree() {
    when(bids.findByStatus(BidStatus.PENDING)).thenReturn(List.of(new FreeAgentBid(1L, 97L, 11L, 2)));
    assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
        () -> service.submitBid(1L, 99L, 10L, 2)).getStatusCode());
    assertEquals(1, service.submitBid(1L, 99L, 10L, 1).getAmount());
  }

  @Test void preservesTenPokemonAndHundredDollarRosterChecks() {
    assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
        () -> service.submitBid(1L, 98L, 10L, 1)).getStatusCode());
    when(roster.countByTeamId(1L)).thenReturn(9L);
    assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
        () -> service.submitBid(1L, 99L, 10L, 1)).getStatusCode());
  }

  @Test void rejectsPickupMoreExpensiveThanDrop() {
    when(pokemon.findById(96L)).thenReturn(Optional.of(new Pokemon("Higher price", 6)));
    assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
        () -> service.submitBid(1L, 96L, 10L, 1)).getStatusCode());
  }

  @Test void acceptsEqualPricePickup() {
    when(pokemon.findById(95L)).thenReturn(Optional.of(new Pokemon("Equal price", 5)));
    assertEquals(1, service.submitBid(1L, 95L, 10L, 1).getAmount());
  }

  @Test void processesTwoSeparateWaiverPeriodsWithoutResettingBudgets() {
    Map<String, Object> first = service.processBids();
    assertEquals(0, first.get("winners"));
    assertEquals(1, first.get("period"));
    assertFalse(config.isWaiverProcessed());
    assertTrue(service.isWaiverWindowOpen());

    Map<String, Object> second = service.processBids();
    assertEquals(2, second.get("period"));
    assertTrue(config.isWaiverProcessed());
    assertFalse(service.isWaiverWindowOpen());
    assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
        service::processBids).getStatusCode());
  }

}
