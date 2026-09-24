package com.pokemondraft.leagueportal.controller;

import com.pokemondraft.leagueportal.model.*;
import com.pokemondraft.leagueportal.repository.*;
import com.pokemondraft.leagueportal.service.AuthService;
import jakarta.servlet.http.HttpSession;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/draft-wheel")
public class DraftWheelController {
  private final AuthService auth;
  private final TeamRepository teams;
  private final DraftPickRepository picks;
  private final LeagueSettingsRepository settings;
  private final SecureRandom random = new SecureRandom();

  public DraftWheelController(AuthService auth, TeamRepository teams,
      DraftPickRepository picks, LeagueSettingsRepository settings) {
    this.auth = auth;
    this.teams = teams;
    this.picks = picks;
    this.settings = settings;
  }

  private List<Long> drawn(LeagueSettings s) {
    String value = s.getDraftWheelResults();
    if (value.isBlank()) return new ArrayList<>();
    List<Long> result = new ArrayList<>();
    for (String part : value.split(",")) result.add(Long.valueOf(part));
    return result;
  }

  private Map<String, Object> view(LeagueSettings s) {
    List<Team> all = teams.findAll();
    List<Long> results = drawn(s);
    List<Map<String, Object>> history = new ArrayList<>();
    for (int i = 0; i < results.size(); i++) {
      Long id = results.get(i);
      Team team = all.stream().filter(t -> t.getId().equals(id)).findFirst().orElse(null);
      if (team != null) history.add(Map.of("teamId", id, "name", team.getName(), "position", all.size() - i));
    }
    List<Map<String, Object>> remaining = new ArrayList<>();
    for (Team t : all) if (!results.contains(t.getId()))
      remaining.add(Map.of("teamId", t.getId(), "name", t.getName()));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("history", history);
    out.put("remaining", remaining);
    out.put("complete", all.size() == 16 && results.size() == 16);
    return out;
  }

  @GetMapping
  public Map<String, Object> get(HttpSession session) {
    auth.current(session);
    return view(settings.findById(1L).orElseGet(LeagueSettings::new));
  }

  @PostMapping("/spin")
  @Transactional
  public Map<String, Object> spin(HttpSession session) {
    auth.manager(session);
    if (picks.count() != 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot change draft order after picks have been made");
    List<Team> all = teams.findAll();
    if (all.size() != 16) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Wheel requires exactly 16 teams");
    LeagueSettings s = settings.findById(1L).orElseGet(LeagueSettings::new);
    List<Long> history = drawn(s);
    if (history.size() >= 16) throw new ResponseStatusException(HttpStatus.CONFLICT, "Wheel is complete");
    List<Team> remaining = all.stream().filter(t -> !history.contains(t.getId())).toList();
    Team selected = remaining.get(random.nextInt(remaining.size()));
    history.add(selected.getId());
    selected.setDraftPosition(17 - history.size());
    teams.save(selected);
    if (history.size() == 15) {
      Team last = all.stream().filter(t -> !history.contains(t.getId())).findFirst().orElseThrow();
      last.setDraftPosition(1);
      teams.save(last);
      history.add(last.getId());
    }
    s.setDraftWheelResults(String.join(",", history.stream().map(String::valueOf).toList()));
    settings.save(s);
    return view(s);
  }

  @PostMapping("/reset")
  @Transactional
  public Map<String, Object> reset(HttpSession session) {
    auth.manager(session);
    if (picks.count() != 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot reset after draft picks have been made");
    LeagueSettings s = settings.findById(1L).orElseGet(LeagueSettings::new);
    s.setDraftWheelResults("");
    settings.save(s);
    return view(s);
  }
}
