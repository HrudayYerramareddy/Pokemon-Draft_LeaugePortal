package com.pokemondraft.leagueportal.repository;

import com.pokemondraft.leagueportal.model.WeeklySubmission;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklySubmissionRepository extends JpaRepository<WeeklySubmission, Long> {
  Optional<WeeklySubmission> findByTeamIdAndWeek(Long teamId, int week);

  List<WeeklySubmission> findByWeek(int week);
}
