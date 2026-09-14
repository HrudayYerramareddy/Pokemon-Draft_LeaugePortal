package com.pokemondraft.leagueportal.repository;
import com.pokemondraft.leagueportal.model.TradeOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface TradeOfferRepository extends JpaRepository<TradeOffer,Long>{ List<TradeOffer> findByProposerTeamIdOrRecipientTeamIdOrderByCreatedAtDesc(Long proposer,Long recipient); }
