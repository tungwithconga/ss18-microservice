package com.rikkeibank.transaction.repository;

import com.rikkeibank.transaction.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByTransactionRef(String transactionRef);
    List<Transaction> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    List<Transaction> findBySourceAccountNoOrTargetAccountNo(String sourceAccountNo, String targetAccountNo);
    List<Transaction> findByInitiatedBy(Long userId);
}
