package com.pokemondraft.leagueportal.repository;

import com.pokemondraft.leagueportal.model.TransactionRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRecordRepository extends JpaRepository<TransactionRecord, Long> {
  List<TransactionRecord> findAllByOrderByCreatedAtDesc();
}
