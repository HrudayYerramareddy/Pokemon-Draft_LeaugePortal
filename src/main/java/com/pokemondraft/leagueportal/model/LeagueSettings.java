package com.pokemondraft.leagueportal.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class LeagueSettings {
  @Id private Long id = 1L;
  private String leagueName = "Pokemon Draft League Portal";
  private int currentWeek = 1;
  private int regularSeasonWeeks = 10;
  private boolean draftOpen = true;
  private boolean snakeDraft = true;
  private long scheduleSeed = 2026L;
  private LocalDateTime lineupDeadline;

  public LeagueSettings() {}

  public Long getId() {
    return id;
  }

  public String getLeagueName() {
    return leagueName;
  }

  public void setLeagueName(String v) {
    leagueName = v;
  }

  public int getCurrentWeek() {
    return currentWeek;
  }

  public void setCurrentWeek(int v) {
    currentWeek = v;
  }

  public int getRegularSeasonWeeks() {
    return regularSeasonWeeks;
  }

  public void setRegularSeasonWeeks(int v) {
    regularSeasonWeeks = v;
  }

  public boolean isDraftOpen() {
    return draftOpen;
  }

  public void setDraftOpen(boolean v) {
    draftOpen = v;
  }

  public boolean isSnakeDraft() {
    return snakeDraft;
  }

  public void setSnakeDraft(boolean v) {
    snakeDraft = v;
  }

  public long getScheduleSeed() {
    return scheduleSeed;
  }

  public void setScheduleSeed(long v) {
    scheduleSeed = v;
  }

  public LocalDateTime getLineupDeadline() {
    return lineupDeadline;
  }

  public void setLineupDeadline(LocalDateTime v) {
    lineupDeadline = v;
  }

  @Transient
  public boolean isPlayoffSeedModeEnabled() {
    return regularSeasonWeeks - currentWeek + 1 <= 2;
  }
}
