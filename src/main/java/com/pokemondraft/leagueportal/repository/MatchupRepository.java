package com.pokemondraft.leagueportal.repository;

import com.pokemondraft.leagueportal.model.Matchup;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchupRepository extends JpaRepository<Matchup, Long> {
  List<Matchup> findAllByOrderByWeekAscIdAsc();

  List<Matchup> findByWeekOrderByIdAsc(int week);
}
