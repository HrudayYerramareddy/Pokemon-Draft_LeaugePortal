package com.pokemondraft.leagueportal.config;

import com.pokemondraft.leagueportal.model.Pokemon;
import com.pokemondraft.leagueportal.repository.PokemonRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class PokemonPoolMigration {
    @Bean
    CommandLineRunner consolidateSquawkabilly(PokemonRepository pokemon) {
        return args -> {
            List<String> oldForms = List.of(
                    "Squawkabilly (Green)",
                    "Squawkabilly (Blue)",
                    "Squawkabilly (Yellow)",
                    "Squawkabilly (White)"
            );

            // A clean/new league gets one Squawkabilly draft slot. Blue Plumage is only
            // the representative API/image form; plumage is not a separate draft entity.
            if (pokemon.findByName("Squawkabilly").isEmpty()) {
                int price = pokemon.findByName("Squawkabilly (Blue)")
                        .map(Pokemon::getPrice)
                        .orElse(0);
                pokemon.save(new Pokemon("Squawkabilly", price));
            }

            // Preserve a drafted legacy form instead of silently deleting a rostered Pokemon.
            // Undrafted legacy form rows are removed immediately.
            for (String oldName : oldForms) {
                pokemon.findByName(oldName).ifPresent(old -> {
                    if (!old.isDrafted()) pokemon.delete(old);
                });
            }
        };
    }
}
