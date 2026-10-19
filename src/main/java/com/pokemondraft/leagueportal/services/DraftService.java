package com.pokemondraft.leagueportal.services;

import com.pokemondraft.leagueportal.entity.*;
import com.pokemondraft.leagueportal.repositories.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DraftService {
  private final TeamRepository teams;
  private final PokemonRepository pokemon;
  private final DraftPickRepository picks;
  private final RosterEntryRepository roster;
  private final LeagueSettingsRepository settings;

  public DraftService(
      TeamRepository teams,
      PokemonRepository pokemon,
      DraftPickRepository picks,
      RosterEntryRepository roster,
      LeagueSettingsRepository settings) {
    this.teams = teams;
    this.pokemon = pokemon;
    this.picks = picks;
    this.roster = roster;
    this.settings = settings;
  }

  public Team currentTeam() {
    List<Team> order = teams.findAll();
    order.sort(Comparator.comparingInt(Team::getDraftPosition));
    if (order.size() != 16)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The draft cannot start until the commissioner has configured all 16 teams.");
    long count = picks.count();
    if (count >= 160) return null;
    int round = (int) (count / 16);
    int slot = (int) (count % 16);
    boolean snake = settings.findById(1L).orElseGet(LeagueSettings::new).isSnakeDraft();
    if (snake && round % 2 == 1) slot = 15 - slot;
    return order.get(slot);
  }

  @Transactional
  public DraftPick pick(Long actingTeamId, boolean manager, Long pokemonId) {
    LeagueSettings s = settings.findById(1L).orElseGet(LeagueSettings::new);
    if (!s.isDraftOpen())
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The commissioner has closed the draft. You cannot make picks right now.");
    Team current = currentTeam();
    if (current == null)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "All 160 draft picks are complete. No more Pokémon can be selected.");
    if (!manager && !current.getId().equals(actingTeamId))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "It is " + current.getName() + "\'s turn to draft. Wait for your team to be on the clock.");
    if (pokemonId == null)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a Pokémon before drafting.");
    Pokemon p = pokemon.findById(pokemonId).orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND, "That Pokémon is no longer in the draft pool."));
    if (p.isDrafted() || roster.findByPokemonId(pokemonId).isPresent())
      throw new ResponseStatusException(HttpStatus.CONFLICT, p.getName() + " has already been drafted. Select an available Pokémon.");
    if (roster.countByTeamId(current.getId()) >= 10)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, current.getName() + " already has 10 Pokémon and cannot draft another.");
    int overall = (int) picks.count() + 1;
    int round = (overall - 1) / 16 + 1;
    p.setDrafted(true);
    pokemon.save(p);
    roster.save(new RosterEntry(current.getId(), p.getId()));
    return picks.save(new DraftPick(overall, round, current.getId(), p.getId()));
  }

  @Transactional
  public void undoLast() {
    List<DraftPick> all = picks.findAllByOrderByOverallPickAsc();
    if (all.isEmpty()) return;
    DraftPick last = all.get(all.size() - 1);
    roster.findByPokemonId(last.getPokemonId()).ifPresent(roster::delete);
    pokemon
        .findById(last.getPokemonId())
        .ifPresent(
            p -> {
              p.setDrafted(false);
              pokemon.save(p);
            });
    picks.delete(last);
  }
}
