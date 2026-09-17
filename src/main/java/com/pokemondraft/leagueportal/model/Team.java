package com.pokemondraft.leagueportal.model;

import jakarta.persistence.*;

@Entity
public class Team {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(unique = true, nullable = false)
  private String name;

  private String coachName;

  @Column(nullable = false)
  private String division;

  private int wins;
  private int losses;
  private int differential;
  private int faBudget = 100;
  private int draftPosition;
  private int manualRank;

  public Team() {}

  public Team(String name, String coachName, String division, int draftPosition) {
    this.name = name;
    this.coachName = coachName;
    this.division = division;
    this.draftPosition = draftPosition;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String v) {
    name = v;
  }

  public String getCoachName() {
    return coachName;
  }

  public void setCoachName(String v) {
    coachName = v;
  }

  public String getDivision() {
    return division;
  }

  public void setDivision(String v) {
    division = v;
  }

  public int getWins() {
    return wins;
  }

  public void setWins(int v) {
    wins = v;
  }

  public int getLosses() {
    return losses;
  }

  public void setLosses(int v) {
    losses = v;
  }

  public int getDifferential() {
    return differential;
  }

  public void setDifferential(int v) {
    differential = v;
  }

  public int getFaBudget() {
    return faBudget;
  }

  public void setFaBudget(int v) {
    faBudget = v;
  }

  public int getDraftPosition() {
    return draftPosition;
  }

  public void setDraftPosition(int v) {
    draftPosition = v;
  }

  public int getManualRank() {
    return manualRank;
  }

  public void setManualRank(int v) {
    manualRank = v;
  }
}
