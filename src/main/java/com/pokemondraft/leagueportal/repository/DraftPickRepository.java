package com.pokemondraft.leagueportal.repository;
import com.pokemondraft.leagueportal.model.DraftPick;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface DraftPickRepository extends JpaRepository<DraftPick,Long>{ List<DraftPick> findAllByOrderByOverallPickAsc(); }
