package com.pokemondraft.leagueportal.services;

import com.pokemondraft.leagueportal.entity.AppUser;
import com.pokemondraft.leagueportal.entity.Role;
import com.pokemondraft.leagueportal.entity.Team;
import com.pokemondraft.leagueportal.repositories.AppUserRepository;
import com.pokemondraft.leagueportal.repositories.TeamRepository;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Safe, repeatable upgrade of the existing Season 2 identities.
 *
 * Teams and accounts retain their database IDs; roster entries, draft picks,
 * matchups, password hashes and sessions continue to reference the same rows.
 * Renames are completed in two phases inside one transaction because name
 * and username are unique columns and a name may belong to another old slot.
 */
@Service
public class Season2IdentityService {
  private static final Logger log = LoggerFactory.getLogger(Season2IdentityService.class);

  private static final List<String> TEAM_NAMES = List.of(
      "Burn", "Matty/Shuckle", "JackApples", "Wide Right",
      "BryloRen", "TBlocks", "Rover", "ArmTheSharks",
      "Sparky", "CoachKief", "MikeO", "Zandy",
      "DonnyKong", "Zeke", "Matt3024", "CWC");

  private static final List<String> COACH_NAMES = List.of(
      "Bingus", "Shuckle2306", "Garrett", "wide_right_",
      "Brylo", "TBlocks", "Rover", "Sharks",
      "Sparky", "Kief", "MikeO", "Zandy",
      "Pudding", "Zeke", "TheMartian", "CWC");

  private static final Set<Integer> KANTO = Set.of(1, 2, 4, 7, 8, 14, 15, 16);
  private static final Set<String> LEGACY_TEAM_NAMES =
      Set.of("Bacon", "Matt0306", "coachKief", "CoachKief", "MCRover", "MikeOAndHisDoh");

  private final TeamRepository teams;
  private final AppUserRepository users;

  public Season2IdentityService(TeamRepository teams, AppUserRepository users) {
    this.teams = teams;
    this.users = users;
  }

  @Transactional
  public void reconcile() {
    List<Team> allTeams = teams.findAll();
    if (allTeams.size() != 16) {
      log.warn("Skipping Season 2 identity migration: expected 16 teams, found {}", allTeams.size());
      return;
    }

    Map<Integer, Team> bySlot = new HashMap<>();
    for (Team team : allTeams) {
      if (team.getDraftPosition() < 1 || team.getDraftPosition() > 16
          || bySlot.putIfAbsent(team.getDraftPosition(), team) != null) {
        log.warn("Skipping team rename: invalid or duplicate draft positions; preserving existing identities");
        return;
      }
    }

    Map<String, Team> owners = allTeams.stream()
        .collect(Collectors.toMap(Team::getName, t -> t));
    Map<Long, String> plannedNames = new LinkedHashMap<>();
    for (int slot = 1; slot <= 16; slot++) {
      Team team = bySlot.get(slot);
      String current = team.getName();
      String target = TEAM_NAMES.get(slot - 1);
      boolean managed = ("Team " + slot).equals(current)
          || TEAM_NAMES.contains(current) || LEGACY_TEAM_NAMES.contains(current);
      if (!managed || current.equals(target)) continue;

      Team currentOwner = owners.get(target);
      if (currentOwner != null && !currentOwner.getId().equals(team.getId())) {
        int ownerSlot = currentOwner.getDraftPosition();
        String ownerDesired = TEAM_NAMES.get(ownerSlot - 1);
        boolean willMove = !target.equals(ownerDesired)
            && (TEAM_NAMES.contains(currentOwner.getName())
                || LEGACY_TEAM_NAMES.contains(currentOwner.getName())
                || ("Team " + ownerSlot).equals(currentOwner.getName()));
        if (!willMove) {
          log.warn("Preserving existing team name {} on team id {}; cannot assign it to slot {}",
              target, currentOwner.getId(), slot);
          continue;
        }
      }
      plannedNames.put(team.getId(), target);
    }

    // Remove blocked rename chains (A needs B's name, but B cannot move).
    // Never let a partially-managed existing database trigger a unique error.
    boolean pruned;
    do {
      pruned = false;
      for (Map.Entry<Long, String> entry : new ArrayList<>(plannedNames.entrySet())) {
        Team owner = owners.get(entry.getValue());
        if (owner != null && !owner.getId().equals(entry.getKey())
            && !plannedNames.containsKey(owner.getId())) {
          log.warn("Cannot rename team {} to {}; existing owner {} is not moving",
              entry.getKey(), entry.getValue(), owner.getId());
          plannedNames.remove(entry.getKey());
          pruned = true;
        }
      }
    } while (pruned);

    // Stage every team that will be renamed first. This frees all unique
    // names before final assignments, including swaps and chains.
    for (Team team : allTeams) {
      if (!plannedNames.containsKey(team.getId())) continue;
      team.setName("__s2_team_migrating_" + team.getId());
      teams.saveAndFlush(team);
    }
    for (Team team : allTeams) {
      String target = plannedNames.get(team.getId());
      if (target != null) {
        team.setName(target);
        teams.saveAndFlush(team);
      }
      int slot = team.getDraftPosition();
      // Align the published divisions even if the team's display name was customized.
      String division = KANTO.contains(slot) ? "A" : "B";
      if (!division.equals(team.getDivision())) team.setDivision(division);
      String desiredCoach = COACH_NAMES.get(slot - 1);
      if (("Coach " + slot).equals(team.getCoachName())
          || COACH_NAMES.contains(team.getCoachName())) {
        team.setCoachName(desiredCoach);
      }
      teams.save(team);
    }
    teams.flush();

    // Coach account names are also unique; stage any swaps before finalizing.
    Map<Long, String> coachByTeam = allTeams.stream()
        .collect(Collectors.toMap(Team::getId, Team::getCoachName));
    List<AppUser> accounts = users.findAll();
    Map<String, AppUser> byUsername = accounts.stream()
        .collect(Collectors.toMap(AppUser::getUsername, u -> u));
    Map<Long, String> loginRenames = new LinkedHashMap<>();
    for (AppUser user : accounts) {
      if (user.getRole() != Role.COACH || user.getTeamId() == null) continue;
      String target = coachByTeam.get(user.getTeamId());
      if (target == null || target.isBlank() || target.equals(user.getUsername())) continue;
      AppUser owner = byUsername.get(target);
      if (owner != null && !owner.getId().equals(user.getId())) {
        String ownerTarget = coachByTeam.get(owner.getTeamId());
        if (owner.getRole() != Role.COACH || target.equals(ownerTarget)) {
          log.warn("Coach login {} already belongs to user {}; preserving account {}",
              target, owner.getId(), user.getId());
          continue;
        }
      }
      loginRenames.put(user.getId(), target);
    }
    boolean loginsPruned;
    do {
      loginsPruned = false;
      for (Map.Entry<Long, String> entry : new ArrayList<>(loginRenames.entrySet())) {
        AppUser owner = byUsername.get(entry.getValue());
        if (owner != null && !owner.getId().equals(entry.getKey())
            && !loginRenames.containsKey(owner.getId())) {
          log.warn("Cannot rename user {} to {}; login held by user {}",
              entry.getKey(), entry.getValue(), owner.getId());
          loginRenames.remove(entry.getKey());
          loginsPruned = true;
        }
      }
    } while (loginsPruned);

    for (AppUser user : accounts) {
      if (!loginRenames.containsKey(user.getId())) continue;
      user.setUsername("__s2_login_migrating_" + user.getId());
      users.saveAndFlush(user);
    }
    for (AppUser user : accounts) {
      String target = loginRenames.get(user.getId());
      if (target != null) {
        user.setUsername(target);
        users.saveAndFlush(user);
      }
    }
    log.info("Season 2 identities synchronized: {} team names, {} coach logins",
        plannedNames.size(), loginRenames.size());
  }
}
