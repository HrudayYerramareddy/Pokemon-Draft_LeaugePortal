package com.pokemondraft.leagueportal.repository;
import com.pokemondraft.leagueportal.model.TransactionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface TransactionRecordRepository extends JpaRepository<TransactionRecord,Long>{ List<TransactionRecord> findAllByOrderByCreatedAtDesc(); }
