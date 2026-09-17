package com.pokemondraft.leagueportal.repository;

import com.pokemondraft.leagueportal.model.*;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FreeAgentBidRepository extends JpaRepository<FreeAgentBid, Long> {
  List<FreeAgentBid> findByTeamIdOrderByCreatedAtDesc(Long teamId);

  List<FreeAgentBid> findByStatus(BidStatus status);
}
