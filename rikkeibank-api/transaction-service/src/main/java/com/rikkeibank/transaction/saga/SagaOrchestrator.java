package com.rikkeibank.transaction.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rikkeibank.transaction.client.AccountServiceClient;
import com.rikkeibank.transaction.dto.*;
import com.rikkeibank.transaction.entity.SagaState;
import com.rikkeibank.transaction.entity.Transaction;
import com.rikkeibank.transaction.event.*;
import com.rikkeibank.transaction.repository.SagaStateRepository;
import com.rikkeibank.transaction.repository.TransactionRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ═══════════════════════════════════════════════════════════════
 * SAGA ORCHESTRATOR — Điều phối luồng chuyển khoản 2 tài khoản
 * ═══════════════════════════════════════════════════════════════
 *
 * LUỒNG THÀNH CÔNG:
 *   STEP 1: Tạo Transaction (PENDING) + SagaState (STARTED)
 *   STEP 2: Gọi account-service DEBIT sourceAccount
 *           → SagaState: DEBIT_DONE
 *   STEP 3: Gọi account-service CREDIT targetAccount
 *           → SagaState: CREDIT_DONE → COMPLETED
 *   STEP 4: Cập nhật Transaction (SUCCESS)
 *           → Publish TransferSuccessEvent lên Kafka
 *
 * LUỒNG ROLLBACK (Compensating):
 *   STEP 2 fail → SagaState: FAILED, Transaction: FAILED
 *   STEP 3 fail → COMPENSATE: reverse DEBIT trên sourceAccount
 *               → SagaState: COMPENSATED, Transaction: COMPENSATED
 *               → Publish TransferFailedEvent lên Kafka
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SagaOrchestrator {

    private final TransactionRepository transactionRepository;
    private final SagaStateRepository sagaStateRepository;
    private final AccountServiceClient accountServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final String TOPIC_TRANSFER_SUCCESS = "transfer.success";
    private static final String TOPIC_TRANSFER_FAILED  = "transfer.failed";

    @Transactional
    public TransactionResponse execute(TransferRequest request, Long initiatedBy) {
        String ref = UUID.randomUUID().toString();
        log.info("[SAGA START] ref={}, {} → {}, amount={}", ref,
                request.getSourceAccountNo(), request.getTargetAccountNo(), request.getAmount());

        // ── STEP 1: Tạo Transaction record ─────────────────────────────────────
        Transaction tx = Transaction.builder()
                .transactionRef(ref)
                .sourceAccountNo(request.getSourceAccountNo())
                .targetAccountNo(request.getTargetAccountNo())
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "VND")
                .description(request.getDescription())
                .initiatedBy(initiatedBy)
                .build();
        transactionRepository.save(tx);

        // Lưu SagaState
        SagaState sagaState = SagaState.builder()
                .transactionRef(ref)
                .currentStep("STARTED")
                .status(SagaState.SagaStatus.STARTED)
                .payload(toJson(request))
                .build();
        sagaStateRepository.save(sagaState);

        // ── STEP 2: DEBIT source account ───────────────────────────────────────
        try {
            debitWithCircuitBreaker(request.getSourceAccountNo(), request.getAmount(), ref);
            sagaState.setCurrentStep("DEBIT_DONE");
            sagaState.setStatus(SagaState.SagaStatus.DEBIT_DONE);
            sagaStateRepository.save(sagaState);
            log.info("[SAGA] STEP 2 DEBIT done. ref={}", ref);
        } catch (Exception e) {
            log.error("[SAGA] STEP 2 DEBIT FAILED. ref={}, reason={}", ref, e.getMessage());
            markFailed(tx, sagaState, "DEBIT failed: " + e.getMessage());
            publishFailedEvent(ref, request, "DEBIT_FAILED: " + e.getMessage());
            return TransactionResponse.from(tx);
        }

        // ── STEP 3: CREDIT target account ──────────────────────────────────────
        try {
            creditWithCircuitBreaker(request.getTargetAccountNo(), request.getAmount(), ref);
            sagaState.setCurrentStep("CREDIT_DONE");
            sagaState.setStatus(SagaState.SagaStatus.COMPLETED);
            sagaStateRepository.save(sagaState);
            log.info("[SAGA] STEP 3 CREDIT done. ref={}", ref);
        } catch (Exception e) {
            log.error("[SAGA] STEP 3 CREDIT FAILED. ref={}, reason={}. Starting compensation...", ref, e.getMessage());

            // ── COMPENSATE: Hoàn tiền về tài khoản nguồn ──────────────────────
            sagaState.setCurrentStep("COMPENSATING");
            sagaState.setStatus(SagaState.SagaStatus.COMPENSATING);
            sagaStateRepository.save(sagaState);

            try {
                accountServiceClient.reverseDebit(new SagaOperationRequest(
                        request.getSourceAccountNo(), request.getAmount(), ref));
                log.warn("[SAGA COMPENSATED] Reversed debit on {}. ref={}", request.getSourceAccountNo(), ref);
            } catch (Exception compensateEx) {
                log.error("[SAGA COMPENSATE FAILED] Manual intervention required! ref={}", ref, compensateEx);
            }

            tx.setStatus(Transaction.Status.COMPENSATED);
            tx.setCompletedAt(LocalDateTime.now());
            transactionRepository.save(tx);

            sagaState.setStatus(SagaState.SagaStatus.FAILED);
            sagaState.setCurrentStep("COMPENSATED");
            sagaStateRepository.save(sagaState);

            publishFailedEvent(ref, request, "CREDIT_FAILED + COMPENSATED: " + e.getMessage());
            return TransactionResponse.from(tx);
        }

        // ── STEP 4: SUCCESS ────────────────────────────────────────────────────
        tx.setStatus(Transaction.Status.SUCCESS);
        tx.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(tx);
        log.info("[SAGA SUCCESS] ref={}", ref);

        // Publish success event → notification-service nhận
        kafkaTemplate.send(TOPIC_TRANSFER_SUCCESS, ref, new TransferSuccessEvent(
                ref, request.getSourceAccountNo(), request.getTargetAccountNo(),
                request.getAmount(), LocalDateTime.now()
        ));

        return TransactionResponse.from(tx);
    }

    @CircuitBreaker(name = "account-service", fallbackMethod = "debitFallback")
    private void debitWithCircuitBreaker(String accountNo, java.math.BigDecimal amount, String ref) {
        accountServiceClient.debit(new SagaOperationRequest(accountNo, amount, ref));
    }

    @CircuitBreaker(name = "account-service", fallbackMethod = "creditFallback")
    private void creditWithCircuitBreaker(String accountNo, java.math.BigDecimal amount, String ref) {
        accountServiceClient.credit(new SagaOperationRequest(accountNo, amount, ref));
    }

    // Fallback methods — Circuit Breaker sẽ gọi khi account-service OPEN
    private void debitFallback(String accountNo, java.math.BigDecimal amount, String ref, Exception e) {
        log.error("[CIRCUIT BREAKER OPEN] Cannot debit {}. ref={}", accountNo, ref);
        throw new RuntimeException("account-service circuit breaker OPEN: " + e.getMessage());
    }

    private void creditFallback(String accountNo, java.math.BigDecimal amount, String ref, Exception e) {
        log.error("[CIRCUIT BREAKER OPEN] Cannot credit {}. ref={}", accountNo, ref);
        throw new RuntimeException("account-service circuit breaker OPEN: " + e.getMessage());
    }

    private void markFailed(Transaction tx, SagaState sagaState, String reason) {
        tx.setStatus(Transaction.Status.FAILED);
        tx.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(tx);
        sagaState.setStatus(SagaState.SagaStatus.FAILED);
        sagaStateRepository.save(sagaState);
    }

    private void publishFailedEvent(String ref, TransferRequest request, String reason) {
        kafkaTemplate.send(TOPIC_TRANSFER_FAILED, ref, new TransferFailedEvent(
                ref, request.getSourceAccountNo(), request.getTargetAccountNo(),
                request.getAmount(), reason, LocalDateTime.now()
        ));
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
