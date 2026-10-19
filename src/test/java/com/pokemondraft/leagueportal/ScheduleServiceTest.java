package com.pokemondraft.leagueportal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.pokemondraft.leagueportal.entity.*;
import com.pokemondraft.leagueportal.repositories.*;
import com.pokemondraft.leagueportal.services.ScheduleService;
import com.pokemondraft.leagueportal.services.WeekLockService;
import java.util.*;
import org.junit.jupiter.api.Test;

class ScheduleServiceTest {
  @Test void recordingAndClearingResultsRecalculatesStandings() {
    TeamRepository teams = mock(TeamRepository.class);
    MatchupRepository matchups = mock(MatchupRepository.class);
    LeagueSettingsRepository settings = mock(LeagueSettingsRepository.class);
    ScheduleService schedule = new ScheduleService(teams, matchups, settings, mock(WeekLockService.class));
    Team home = new Team("Home", "Coach", "A", 1);
    Team away = new Team("Away", "Coach", "B", 2);
    org.springframework.test.util.ReflectionTestUtils.setField(home, "id", 1L);
    org.springframework.test.util.ReflectionTestUtils.setField(away, "id", 2L);
    Matchup match = new Matchup(1, 1L, 2L);
    when(matchups.findById(5L)).thenReturn(Optional.of(match));
    when(matchups.findAll()).thenReturn(List.of(match));
    when(teams.findAll()).thenReturn(List.of(home, away));
    schedule.setResult(5L, 2, 1);
    assertTrue(match.isPlayed());
    assertEquals(1, home.getWins());
    assertEquals(1, away.getLosses());
    assertEquals(1, home.getDifferential());
    schedule.clearResult(5L);
    assertFalse(match.isPlayed());
    assertEquals(0, home.getWins());
    assertEquals(0, away.getLosses());
    assertEquals(0, home.getDifferential());
  }
  @Test void onlyLegalBestOfThreeScoresAreAllowed() {
    for (int[] good : new int[][]{{2,0},{2,1},{0,2},{1,2}})
      assertTrue(ScheduleService.validSeriesScore(good[0],good[1]));
    for (int[] bad : new int[][]{{0,0},{1,1},{1,0},{2,2},{3,0},{4,2},{-1,2}})
      assertFalse(ScheduleService.validSeriesScore(bad[0],bad[1]));
    assertFalse(ScheduleService.validSeriesScore(null,2));
  }

  @Test void rejectsInvalidRegularAndPlayoffScoresWithoutSaving() {
    TeamRepository teams = mock(TeamRepository.class);
    MatchupRepository matchups = mock(MatchupRepository.class);
    LeagueSettingsRepository settings = mock(LeagueSettingsRepository.class);
    ScheduleService service = new ScheduleService(teams,matchups,settings,mock(WeekLockService.class));
    Matchup regular = new Matchup(1,1L,2L), playoff = new Matchup(14,1L,2L);
    when(matchups.findById(1L)).thenReturn(Optional.of(regular));
    when(matchups.findById(14L)).thenReturn(Optional.of(playoff));
    for (int[] bad : new int[][]{{1,1},{3,0},{2,2},{0,1},{4,2}}) {
      assertThrows(org.springframework.web.server.ResponseStatusException.class,
          () -> service.setResult(1L,bad[0],bad[1]));
      assertThrows(org.springframework.web.server.ResponseStatusException.class,
          () -> service.setResult(14L,bad[0],bad[1]));
    }
    verify(matchups,never()).save(any(Matchup.class));
  }
}

