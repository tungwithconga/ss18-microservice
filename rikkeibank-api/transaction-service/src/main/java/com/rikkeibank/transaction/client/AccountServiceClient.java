package com.rikkeibank.transaction.client;

import com.rikkeibank.transaction.dto.BalanceUpdateResponse;
import com.rikkeibank.transaction.dto.SagaOperationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * FeignClient để gọi account-service qua Eureka.
 * Spring Cloud LoadBalancer tự động load balance giữa các instance.
 * Circuit Breaker được áp dụng trong SagaOrchestrator.
 */
@FeignClient(name = "account-service", fallback = AccountServiceClientFallback.class)
public interface AccountServiceClient {

    @PostMapping("/api/accounts/internal/debit")
    BalanceUpdateResponse debit(@RequestBody SagaOperationRequest request);

    @PostMapping("/api/accounts/internal/credit")
    BalanceUpdateResponse credit(@RequestBody SagaOperationRequest request);

    @PostMapping("/api/accounts/internal/reverse-debit")
    void reverseDebit(@RequestBody SagaOperationRequest request);
}
