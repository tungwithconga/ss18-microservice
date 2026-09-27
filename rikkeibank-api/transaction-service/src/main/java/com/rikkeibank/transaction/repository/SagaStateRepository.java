package com.rikkeibank.transaction.repository;

import com.rikkeibank.transaction.entity.SagaState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SagaStateRepository extends JpaRepository<SagaState, Long> {
    Optional<SagaState> findByTransactionRef(String transactionRef);
}
