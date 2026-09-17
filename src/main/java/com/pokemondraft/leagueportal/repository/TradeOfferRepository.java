package com.pokemondraft.leagueportal.repository;

import com.pokemondraft.leagueportal.model.TradeOffer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeOfferRepository extends JpaRepository<TradeOffer, Long> {
  List<TradeOffer> findByProposerTeamIdOrRecipientTeamIdOrderByCreatedAtDesc(
      Long proposer, Long recipient);
}
