package com.pokemondraft.leagueportal.repository;
import com.pokemondraft.leagueportal.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface FreeAgentBidRepository extends JpaRepository<FreeAgentBid,Long>{ List<FreeAgentBid> findByTeamIdOrderByCreatedAtDesc(Long teamId); List<FreeAgentBid> findByStatus(BidStatus status); }
