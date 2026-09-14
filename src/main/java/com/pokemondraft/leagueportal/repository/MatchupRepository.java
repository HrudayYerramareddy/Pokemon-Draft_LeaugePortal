package com.pokemondraft.leagueportal.repository;
import com.pokemondraft.leagueportal.model.Matchup;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface MatchupRepository extends JpaRepository<Matchup,Long>{ List<Matchup> findAllByOrderByWeekAscIdAsc(); List<Matchup> findByWeekOrderByIdAsc(int week); }
