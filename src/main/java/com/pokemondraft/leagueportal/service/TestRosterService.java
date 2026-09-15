package com.pokemondraft.leagueportal.service;

import com.pokemondraft.leagueportal.model.*;
import com.pokemondraft.leagueportal.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class TestRosterService {
    private static final int ROSTER_SIZE = 10;
    private static final int ROSTER_BUDGET = 100;

    private final TeamRepository teams;
    private final PokemonRepository pokemon;
    private final RosterEntryRepository roster;
    private final DraftPickRepository picks;
    private final WeeklySubmissionRepository submissions;
    private final TransactionRecordRepository transactions;

    public TestRosterService(TeamRepository teams, PokemonRepository pokemon, RosterEntryRepository roster,
                             DraftPickRepository picks, WeeklySubmissionRepository submissions,
                             TransactionRecordRepository transactions) {
        this.teams = teams;
        this.pokemon = pokemon;
        this.roster = roster;
        this.picks = picks;
        this.submissions = submissions;
        this.transactions = transactions;
    }

    @Transactional
    public Map<String,Integer> fillTestRosters() {
        if (roster.count() != 0 || picks.count() != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Clear rosters first, then use Fill Test Rosters.");
        }

        List<Team> teamList = teams.findAll().stream()
                .sorted(Comparator.comparingInt(Team::getDraftPosition))
                .toList();
        if (teamList.size() != 16) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Test fill needs exactly 16 teams. Found " + teamList.size() + ".");
        }

        List<Pokemon> pool = new ArrayList<>(pokemon.findAll());
        int needed = teamList.size() * ROSTER_SIZE;
        if (pool.size() < needed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Not enough Pokemon to fill every roster. Need " + needed + ", found " + pool.size() + ".");
        }

        /*
         * Use the 160 cheapest Pokemon, then distribute the expensive ones first
         * to the team that currently has the lowest roster value. This avoids the
         * old bug where early teams received all cheap Pokemon and the last teams
         * were left with ten expensive Pokemon that could not fit under $100.
         */
        pool.sort(Comparator.comparingInt(Pokemon::getPrice).thenComparing(Pokemon::getName));
        List<Pokemon> selected = new ArrayList<>(pool.subList(0, needed));
        int selectedTotal = selected.stream().mapToInt(Pokemon::getPrice).sum();
        if (selectedTotal > teamList.size() * ROSTER_BUDGET) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The cheapest 160 Pokemon cost $" + selectedTotal + ", so 16 legal $100 rosters cannot be created.");
        }
        selected.sort(Comparator.comparingInt(Pokemon::getPrice).reversed().thenComparing(Pokemon::getName));

        Map<Long,List<Pokemon>> assignments = new LinkedHashMap<>();
        Map<Long,Integer> values = new HashMap<>();
        for (Team team : teamList) {
            assignments.put(team.getId(), new ArrayList<>());
            values.put(team.getId(), 0);
        }

        for (Pokemon p : selected) {
            Team best = teamList.stream()
                    .filter(t -> assignments.get(t.getId()).size() < ROSTER_SIZE)
                    .filter(t -> values.get(t.getId()) + p.getPrice() <= ROSTER_BUDGET)
                    .min(Comparator.comparingInt((Team t) -> values.get(t.getId()))
                            .thenComparingInt(t -> assignments.get(t.getId()).size())
                            .thenComparingInt(Team::getDraftPosition))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Could not balance the generated rosters under $100. Clear and try again."));
            assignments.get(best.getId()).add(p);
            values.put(best.getId(), values.get(best.getId()) + p.getPrice());
        }

        for (Team team : teamList) {
            if (assignments.get(team.getId()).size() != ROSTER_SIZE || values.get(team.getId()) > ROSTER_BUDGET) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Could not create a legal 10-Pokemon roster for " + team.getName() + ".");
            }
        }

        int assigned = 0;
        for (Team team : teamList) {
            for (Pokemon p : assignments.get(team.getId())) {
                roster.save(new RosterEntry(team.getId(), p.getId()));
                p.setDrafted(true);
                pokemon.save(p);
                assigned++;
            }
        }

        int highestValue = values.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        transactions.save(new TransactionRecord("TEST_SETUP",
                "Commissioner filled all 16 test rosters with 10 Pokemon each. Highest roster value: $" + highestValue + "."));
        return Map.of("teams", teamList.size(), "pokemonAssigned", assigned, "highestRosterValue", highestValue);
    }

    @Transactional
    public Map<String,Integer> clearRosters() {
        int removed = (int) roster.count();
        roster.deleteAll();
        picks.deleteAll();
        submissions.deleteAll();
        for (Pokemon p : pokemon.findAll()) {
            if (p.isDrafted()) {
                p.setDrafted(false);
                pokemon.save(p);
            }
        }
        transactions.save(new TransactionRecord("TEST_RESET",
                "Commissioner cleared all rosters, draft picks, and weekly lineup submissions for testing."));
        return Map.of("pokemonRemoved", removed);
    }
}
