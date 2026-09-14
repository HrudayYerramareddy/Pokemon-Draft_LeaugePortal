package com.pokemondraft.leagueportal.repository;
import com.pokemondraft.leagueportal.model.Pokemon;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PokemonRepository extends JpaRepository<Pokemon,Long>{ List<Pokemon> findAllByOrderByNameAsc(); List<Pokemon> findByDraftedFalseOrderByNameAsc(); }
