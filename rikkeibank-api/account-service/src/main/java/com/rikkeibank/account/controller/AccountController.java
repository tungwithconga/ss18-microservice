package com.rikkeibank.account.controller;

import com.rikkeibank.account.dto.*;
import com.rikkeibank.account.entity.Account;
import com.rikkeibank.account.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public ResponseEntity<List<AccountResponse>> getAll(@RequestHeader("X-User-Role") String role) {
        if (!role.equals("ADMIN") && !role.equals("TELLER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(accountService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getById(
            @PathVariable Long id,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(accountService.getById(id));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<AccountResponse>> getByCustomer(
            @PathVariable Long customerId,
            @RequestHeader("X-User-Role") String role,
            @RequestHeader("X-User-Id") String userId) {
        // CUSTOMER chỉ xem được TK của mình
        return ResponseEntity.ok(accountService.getByCustomerId(customerId));
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(
            @Valid @RequestBody AccountRequest request,
            @RequestHeader("X-User-Role") String role) {
        if (!role.equals("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.create(request));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<AccountResponse> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @RequestHeader("X-User-Role") String role) {
        if (!role.equals("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Account.Status status = Account.Status.valueOf(body.get("status"));
        return ResponseEntity.ok(accountService.updateStatus(id, status));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestHeader("X-User-Role") String role) {
        if (!role.equals("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        accountService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Internal Saga Endpoints (gọi từ transaction-service qua Feign) ──────

    /**
     * POST /api/accounts/internal/debit — Trừ tiền (SAGA STEP 2)
     * Chỉ được gọi từ internal services, không expose ra ngoài trực tiếp
     */
    @PostMapping("/internal/debit")
    public ResponseEntity<BalanceUpdateResponse> debit(@RequestBody SagaOperationRequest request) {
        return ResponseEntity.ok(accountService.debit(
                request.getAccountNo(), request.getAmount(), request.getTransactionRef()));
    }

    /**
     * POST /api/accounts/internal/credit — Cộng tiền (SAGA STEP 3)
     */
    @PostMapping("/internal/credit")
    public ResponseEntity<BalanceUpdateResponse> credit(@RequestBody SagaOperationRequest request) {
        return ResponseEntity.ok(accountService.credit(
                request.getAccountNo(), request.getAmount(), request.getTransactionRef()));
    }

    /**
     * POST /api/accounts/internal/reverse-debit — Hoàn tiền (SAGA COMPENSATE)
     */
    @PostMapping("/internal/reverse-debit")
    public ResponseEntity<Void> reverseDebit(@RequestBody SagaOperationRequest request) {
        accountService.reverseDebit(request.getAccountNo(), request.getAmount(), request.getTransactionRef());
        return ResponseEntity.ok().build();
    }
}
