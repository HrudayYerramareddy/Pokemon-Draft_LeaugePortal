package com.pokemondraft.leagueportal.repository;
import com.pokemondraft.leagueportal.model.RosterEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RosterEntryRepository extends JpaRepository<RosterEntry,Long>{ List<RosterEntry> findByTeamId(Long teamId); Optional<RosterEntry> findByPokemonId(Long pokemonId); long countByTeamId(Long teamId); }
