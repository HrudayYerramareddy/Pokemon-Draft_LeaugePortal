package com.pokemondraft.leagueportal.repository;
import com.pokemondraft.leagueportal.model.WeeklySubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface WeeklySubmissionRepository extends JpaRepository<WeeklySubmission,Long>{ Optional<WeeklySubmission> findByTeamIdAndWeek(Long teamId,int week); List<WeeklySubmission> findByWeek(int week); }
