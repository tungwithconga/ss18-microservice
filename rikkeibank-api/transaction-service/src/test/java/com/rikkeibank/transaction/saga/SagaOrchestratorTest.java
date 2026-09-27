package com.rikkeibank.transaction.saga;

import com.rikkeibank.transaction.client.AccountServiceClient;
import com.rikkeibank.transaction.dto.*;
import com.rikkeibank.transaction.entity.SagaState;
import com.rikkeibank.transaction.entity.Transaction;
import com.rikkeibank.transaction.repository.SagaStateRepository;
import com.rikkeibank.transaction.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit Test cho SagaOrchestrator — kiểm tra:
 * 1. Happy path: DEBIT + CREDIT thành công → Transaction.SUCCESS
 * 2. DEBIT fail  → Transaction.FAILED (không compensate vì chưa DEBIT)
 * 3. CREDIT fail → Transaction.COMPENSATED (reverseDebit được gọi)
 */
@ExtendWith(MockitoExtension.class)
class SagaOrchestratorTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private SagaStateRepository sagaStateRepository;
    @Mock private AccountServiceClient accountServiceClient;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private SagaOrchestrator sagaOrchestrator;

    private TransferRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new TransferRequest();
        validRequest.setSourceAccountNo("RK0000000001");
        validRequest.setTargetAccountNo("RK0000000002");
        validRequest.setAmount(new BigDecimal("500000"));
        validRequest.setCurrency("VND");
        validRequest.setDescription("Test transfer");

        // Mock save behavior
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(sagaStateRepository.save(any(SagaState.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    }

    @Test
    void execute_shouldReturnSuccess_whenDebitAndCreditBothSucceed() {
        // Given
        BalanceUpdateResponse debitResponse = new BalanceUpdateResponse(
                "RK0000000001", "DEBIT", new BigDecimal("500000"),
                new BigDecimal("1000000"), new BigDecimal("500000"), "ref123");
        BalanceUpdateResponse creditResponse = new BalanceUpdateResponse(
                "RK0000000002", "CREDIT", new BigDecimal("500000"),
                new BigDecimal("0"), new BigDecimal("500000"), "ref123");

        when(accountServiceClient.debit(any())).thenReturn(debitResponse);
        when(accountServiceClient.credit(any())).thenReturn(creditResponse);

        // When
        TransactionResponse result = sagaOrchestrator.execute(validRequest, 1L);

        // Then
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        verify(accountServiceClient, times(1)).debit(any());
        verify(accountServiceClient, times(1)).credit(any());
        verify(accountServiceClient, never()).reverseDebit(any());
        verify(kafkaTemplate, times(1)).send(eq("transfer.success"), any(), any());
    }

    @Test
    void execute_shouldReturnFailed_whenDebitFails() {
        // Given
        when(accountServiceClient.debit(any()))
                .thenThrow(new RuntimeException("Insufficient balance"));

        // When
        TransactionResponse result = sagaOrchestrator.execute(validRequest, 1L);

        // Then
        assertThat(result.getStatus()).isEqualTo("FAILED");
        verify(accountServiceClient, times(1)).debit(any());
        verify(accountServiceClient, never()).credit(any());
        verify(accountServiceClient, never()).reverseDebit(any());
        verify(kafkaTemplate, times(1)).send(eq("transfer.failed"), any(), any());
    }

    @Test
    void execute_shouldCompensate_whenCreditFails() {
        // Given — Debit thành công, Credit thất bại
        BalanceUpdateResponse debitResponse = new BalanceUpdateResponse(
                "RK0000000001", "DEBIT", new BigDecimal("500000"),
                new BigDecimal("1000000"), new BigDecimal("500000"), "ref123");
        when(accountServiceClient.debit(any())).thenReturn(debitResponse);
        when(accountServiceClient.credit(any()))
                .thenThrow(new RuntimeException("Target account is FROZEN"));

        // When
        TransactionResponse result = sagaOrchestrator.execute(validRequest, 1L);

        // Then: Saga phải gọi reverseDebit để hoàn tiền
        assertThat(result.getStatus()).isEqualTo("COMPENSATED");
        verify(accountServiceClient, times(1)).debit(any());
        verify(accountServiceClient, times(1)).credit(any());
        verify(accountServiceClient, times(1)).reverseDebit(any());    // ← Compensating!
        verify(kafkaTemplate, times(1)).send(eq("transfer.failed"), any(), any());
    }
}
