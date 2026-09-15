package com.pokemondraft.leagueportal.config;

import com.pokemondraft.leagueportal.model.Pokemon;
import com.pokemondraft.leagueportal.repository.PokemonRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PokemonPoolMigration {
    private final PokemonRepository pokemon;

    public PokemonPoolMigration(PokemonRepository pokemon) {
        this.pokemon = pokemon;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void consolidateSquawkabilly() {
        List<String> oldForms = List.of(
                "Squawkabilly (Green)",
                "Squawkabilly (Blue)",
                "Squawkabilly (Yellow)",
                "Squawkabilly (White)"
        );

        // One league/draft entry. Blue Plumage is only its representative image/API form.
        if (pokemon.findByName("Squawkabilly").isEmpty()) {
            int price = pokemon.findByName("Squawkabilly (Blue)")
                    .map(Pokemon::getPrice)
                    .orElse(0);
            pokemon.save(new Pokemon("Squawkabilly", price));
        }

        // Never silently delete a Pokemon already on a legacy roster.
        for (String oldName : oldForms) {
            pokemon.findByName(oldName).ifPresent(old -> {
                if (!old.isDrafted()) pokemon.delete(old);
            });
        }
    }
}
