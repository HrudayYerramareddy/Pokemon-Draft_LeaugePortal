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
}
