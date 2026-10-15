package com.pokemondraft.leagueportal;

import static org.junit.jupiter.api.Assertions.*;
import com.pokemondraft.leagueportal.entity.*;
import com.pokemondraft.leagueportal.repositories.*;
import com.pokemondraft.leagueportal.services.Season2IdentityService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reproduces the Neon duplicate-name startup failure against a real H2 unique
 * index. Previously renaming the first team would fail because another team
 * already owned the desired name. No team or account row may be replaced.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:identitymigration;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class Season2IdentityMigrationTest {
  @Autowired TeamRepository teams;
  @Autowired AppUserRepository users;
  @Autowired Season2IdentityService identities;

  private Team slot(int n) {
    return teams.findAll().stream().filter(t -> t.getDraftPosition() == n)
        .findFirst().orElseThrow();
  }

  private AppUser account(Long teamId) {
    return users.findAll().stream()
        .filter(u -> Objects.equals(u.getTeamId(), teamId))
        .findFirst().orElseThrow();
  }

  @Test void duplicateTargetNameAndSwappedCoachLoginsAreMigratedWithoutLosingIds() {
    Team zeke = slot(14), matt = slot(15);
    Long zekeId = zeke.getId(), mattId = matt.getId();
    assertEquals("Zeke", zeke.getName());
    assertEquals("Matt3024", matt.getName());

    // Simulate prior Neon state: another database record owns Matt3024.
    zeke.setName("__initial_team_swap"); teams.saveAndFlush(zeke);
    matt.setName("Zeke"); teams.saveAndFlush(matt);
    zeke.setName("Matt3024"); teams.saveAndFlush(zeke);

    AppUser zekeUser = account(zekeId), mattUser = account(mattId);
    Long zekeUserId = zekeUser.getId(), mattUserId = mattUser.getId();
    String zekePassword = zekeUser.getPassword(), mattPassword = mattUser.getPassword();

    zekeUser.setUsername("__initial_login_swap"); users.saveAndFlush(zekeUser);
    mattUser.setUsername("Zeke"); users.saveAndFlush(mattUser);
    zekeUser.setUsername("TheMartian"); users.saveAndFlush(zekeUser);

    assertDoesNotThrow(() -> identities.reconcile());

    assertEquals("Zeke", teams.findById(zekeId).orElseThrow().getName());
    assertEquals("Matt3024", teams.findById(mattId).orElseThrow().getName());
    assertEquals("Zeke", users.findById(zekeUserId).orElseThrow().getUsername());
    assertEquals("TheMartian", users.findById(mattUserId).orElseThrow().getUsername());
    assertEquals(zekePassword, users.findById(zekeUserId).orElseThrow().getPassword());
    assertEquals(mattPassword, users.findById(mattUserId).orElseThrow().getPassword());
    assertEquals(16L, teams.count());
    assertDoesNotThrow(() -> identities.reconcile(), "Repeated Render restarts must be safe");
  }

  @Test void restoresAllOfficialTeamNamesAndDivisionsWithoutRecreatingTeams() {
    identities.reconcile();
    Set<String> kanto = new HashSet<>(), johto = new HashSet<>();
    for (Team team : teams.findAll()) {
      (team.getDivision().equals("A") ? kanto : johto).add(team.getName());
    }
    assertEquals(Set.of("Burn", "Matty/Shuckle", "Wide Right", "Rover",
        "ArmTheSharks", "Zeke", "Matt3024", "CWC"), kanto);
    assertEquals(Set.of("JackApples", "BryloRen", "TBlocks", "Sparky",
        "CoachKief", "MikeO", "Zandy", "DonnyKong"), johto);
  }
}
