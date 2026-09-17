package com.pokemondraft.leagueportal.repository;

import com.pokemondraft.leagueportal.model.Team;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, Long> {
  List<Team> findByDivision(String division);
}
