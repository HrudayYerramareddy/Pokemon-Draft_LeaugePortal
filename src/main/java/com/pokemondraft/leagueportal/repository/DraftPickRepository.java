package com.pokemondraft.leagueportal.repository;

import com.pokemondraft.leagueportal.model.DraftPick;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DraftPickRepository extends JpaRepository<DraftPick, Long> {
  List<DraftPick> findAllByOrderByOverallPickAsc();
}
