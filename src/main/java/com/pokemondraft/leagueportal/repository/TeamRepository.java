package com.pokemondraft.leagueportal.repository;
import com.pokemondraft.leagueportal.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface TeamRepository extends JpaRepository<Team,Long>{ List<Team> findByDivision(String division); }
