package com.pokemondraft.leagueportal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.pokemondraft.leagueportal.entity.*;
import com.pokemondraft.leagueportal.repositories.*;
import com.pokemondraft.leagueportal.services.WeeklyLineupService;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class WeeklyLineupServiceTest {
  WeeklySubmissionRepository submissions = mock(WeeklySubmissionRepository.class);
  RosterEntryRepository roster = mock(RosterEntryRepository.class);
  MatchupRepository matchups = mock(MatchupRepository.class);
  LeagueSettingsRepository settings = mock(LeagueSettingsRepository.class);
  PokemonRepository pokemon = mock(PokemonRepository.class);
  WeeklyLineupService service = new WeeklyLineupService(submissions, roster, matchups, settings, pokemon);
  LeagueSettings config = new LeagueSettings();
  List<Long> six = List.of(10L, 11L, 12L, 13L, 14L, 15L);

  @BeforeEach void setup() {
    when(settings.findById(1L)).thenReturn(Optional.of(config));
    when(matchups.findAllByOrderByWeekAscIdAsc()).thenReturn(List.of(new Matchup(1, 1L, 2L)));
    when(roster.findByTeamId(1L)).thenReturn(six.stream().map(id -> new RosterEntry(1L, id)).toList());
    when(submissions.findByTeamIdAndWeek(anyLong(), anyInt())).thenReturn(Optional.empty());
    when(submissions.save(any(WeeklySubmission.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test void rejectsDuplicateOrIncompleteLineup() {
    assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
        () -> service.submit(1L, List.of(10L, 10L, 12L, 13L, 14L, 15L))).getStatusCode());
    assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
        () -> service.submit(1L, List.of(10L, 11L))).getStatusCode());
  }

  @Test void acceptsSixOwnedPokemon() {
    WeeklySubmission result = service.submit(1L, six);
    assertEquals("10,11,12,13,14,15", result.getPokemonIdsCsv());
    verify(submissions).save(any(WeeklySubmission.class));
  }

  @Test void rejectsNonOwnedPokemon() {
    assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
        () -> service.submit(1L, List.of(10L, 11L, 12L, 13L, 14L, 99L))).getStatusCode());
  }

  @Test void rejectsAfterDeadline() {
    config.setLineupDeadline(LocalDateTime.now().minusMinutes(1));
    assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
        () -> service.submit(1L, six)).getStatusCode());
  }
}
