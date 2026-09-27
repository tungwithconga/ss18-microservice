package com.rikkeibank.transaction.controller;

import com.rikkeibank.transaction.dto.*;
import com.rikkeibank.transaction.entity.SagaState;
import com.rikkeibank.transaction.entity.Transaction;
import com.rikkeibank.transaction.repository.SagaStateRepository;
import com.rikkeibank.transaction.repository.TransactionRepository;
import com.rikkeibank.transaction.saga.SagaOrchestrator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final SagaOrchestrator sagaOrchestrator;
    private final TransactionRepository transactionRepository;
    private final SagaStateRepository sagaStateRepository;

    /**
     * POST /api/transactions/transfer — Khởi tạo chuyển khoản (Saga)
     * Role: CUSTOMER hoặc TELLER
     */
    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role) {

        if (!role.equals("CUSTOMER") && !role.equals("TELLER") && !role.equals("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        TransactionResponse response = sagaOrchestrator.execute(request, Long.parseLong(userId));
        HttpStatus status = response.getStatus().equals("SUCCESS")
                ? HttpStatus.CREATED : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(response);
    }

    /**
     * GET /api/transactions — Tất cả giao dịch (ADMIN, TELLER)
     */
    @GetMapping
    public ResponseEntity<List<TransactionResponse>> getAll(
            @RequestHeader("X-User-Role") String role) {
        if (!role.equals("ADMIN") && !role.equals("TELLER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        List<TransactionResponse> responses = transactionRepository.findAll()
                .stream().map(TransactionResponse::from).toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * GET /api/transactions/today — Giao dịch trong ngày (TELLER)
     */
    @GetMapping("/today")
    public ResponseEntity<List<TransactionResponse>> getToday(
            @RequestHeader("X-User-Role") String role) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(23, 59, 59);
        List<TransactionResponse> responses = transactionRepository
                .findByCreatedAtBetween(startOfDay, endOfDay)
                .stream().map(TransactionResponse::from).toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * GET /api/transactions/{ref} — Chi tiết giao dịch theo ref
     */
    @GetMapping("/{ref}")
    public ResponseEntity<TransactionResponse> getByRef(
            @PathVariable String ref,
            @RequestHeader("X-User-Role") String role) {
        return transactionRepository.findByTransactionRef(ref)
                .map(tx -> ResponseEntity.ok(TransactionResponse.from(tx)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/transactions/account/{accountNo} — Lịch sử TK
     */
    @GetMapping("/account/{accountNo}")
    public ResponseEntity<List<TransactionResponse>> getByAccount(
            @PathVariable String accountNo,
            @RequestHeader("X-User-Role") String role) {
        List<TransactionResponse> responses = transactionRepository
                .findBySourceAccountNoOrTargetAccountNo(accountNo, accountNo)
                .stream().map(TransactionResponse::from).toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * GET /api/transactions/saga/{ref}/status — Trạng thái Saga (ADMIN)
     */
    @GetMapping("/saga/{ref}/status")
    public ResponseEntity<SagaState> getSagaStatus(
            @PathVariable String ref,
            @RequestHeader("X-User-Role") String role) {
        if (!role.equals("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return sagaStateRepository.findByTransactionRef(ref)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
