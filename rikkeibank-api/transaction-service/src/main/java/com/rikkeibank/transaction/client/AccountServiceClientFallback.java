package com.rikkeibank.transaction.client;

import com.rikkeibank.transaction.dto.BalanceUpdateResponse;
import com.rikkeibank.transaction.dto.SagaOperationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback khi account-service không khả dụng (Circuit Breaker OPEN).
 * Ném exception để SagaOrchestrator xử lý compensate.
 */
@Slf4j
@Component
public class AccountServiceClientFallback implements AccountServiceClient {

    @Override
    public BalanceUpdateResponse debit(SagaOperationRequest request) {
        log.error("[CIRCUIT BREAKER] account-service unavailable for DEBIT. ref={}", request.getTransactionRef());
        throw new RuntimeException("account-service is unavailable. Circuit Breaker activated.");
    }

    @Override
    public BalanceUpdateResponse credit(SagaOperationRequest request) {
        log.error("[CIRCUIT BREAKER] account-service unavailable for CREDIT. ref={}", request.getTransactionRef());
        throw new RuntimeException("account-service is unavailable. Circuit Breaker activated.");
    }

    @Override
    public void reverseDebit(SagaOperationRequest request) {
        log.error("[CIRCUIT BREAKER] account-service unavailable for REVERSE DEBIT. ref={}", request.getTransactionRef());
        // Không ném exception ở đây — compensate sẽ retry qua Kafka
    }
}
