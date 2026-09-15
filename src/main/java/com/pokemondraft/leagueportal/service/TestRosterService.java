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
                    "Clear rosters/draft first. Test fill only runs from an empty draft state.");
        }

        List<Team> teamList = teams.findAll().stream()
                .sorted(Comparator.comparingInt(Team::getDraftPosition))
                .toList();
        if (teamList.size() != 16) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Test fill expects 16 teams");
        }

        List<Pokemon> pool = new ArrayList<>(pokemon.findAll());
        pool.sort(Comparator.comparingInt(Pokemon::getPrice).thenComparing(Pokemon::getName));
        if (pool.size() < teamList.size() * ROSTER_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not enough Pokemon to fill every roster");
        }

        // Deterministic test assignment. Cheap Pokemon are distributed first so every
        // generated roster is guaranteed to stay within the normal $100 roster cap.
        int assigned = 0;
        int cursor = 0;
        for (Team team : teamList) {
            int value = 0;
            for (int slot = 0; slot < ROSTER_SIZE; slot++) {
                Pokemon chosen = null;
                int chosenIndex = -1;
                for (int i = cursor; i < pool.size(); i++) {
                    Pokemon candidate = pool.get(i);
                    if (!candidate.isDrafted() && value + candidate.getPrice() <= ROSTER_BUDGET) {
                        chosen = candidate;
                        chosenIndex = i;
                        break;
                    }
                }
                if (chosen == null) {
                    for (int i = 0; i < pool.size(); i++) {
                        Pokemon candidate = pool.get(i);
                        if (!candidate.isDrafted() && value + candidate.getPrice() <= ROSTER_BUDGET) {
                            chosen = candidate;
                            chosenIndex = i;
                            break;
                        }
                    }
                }
                if (chosen == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Could not build all test rosters under the $100 cap");
                }
                roster.save(new RosterEntry(team.getId(), chosen.getId()));
                chosen.setDrafted(true);
                pokemon.save(chosen);
                value += chosen.getPrice();
                assigned++;
                cursor = Math.min(chosenIndex + 1, pool.size());
            }
        }
        transactions.save(new TransactionRecord("TEST_SETUP",
                "Commissioner filled all 16 test rosters with 10 Pokemon each under the $100 roster cap."));
        return Map.of("teams", teamList.size(), "pokemonAssigned", assigned);
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
