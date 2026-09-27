package com.rikkeibank.account.service;

import com.rikkeibank.account.dto.*;
import com.rikkeibank.account.entity.Account;
import com.rikkeibank.account.exception.BusinessException;
import com.rikkeibank.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    public List<AccountResponse> getAll() {
        return accountRepository.findAll().stream().map(AccountResponse::from).toList();
    }

    @Cacheable(value = "account", key = "#id")
    public AccountResponse getById(Long id) {
        log.info("[CACHE MISS] Loading account {} from DB", id);
        return AccountResponse.from(findAccountById(id));
    }

    public List<AccountResponse> getByCustomerId(Long customerId) {
        return accountRepository.findByCustomerId(customerId)
                .stream().map(AccountResponse::from).toList();
    }

    @Transactional
    public AccountResponse create(AccountRequest request) {
        String accountNumber = generateAccountNumber();
        Account account = Account.builder()
                .accountNumber(accountNumber)
                .customerId(request.getCustomerId())
                .accountTypeId(request.getAccountTypeId())
                .balance(request.getInitialBalance() != null ? request.getInitialBalance() : BigDecimal.ZERO)
                .currency(request.getCurrency() != null ? request.getCurrency() : "VND")
                .build();
        return AccountResponse.from(accountRepository.save(account));
    }

    @Transactional
    @CacheEvict(value = "account", key = "#id")
    public AccountResponse updateStatus(Long id, Account.Status status) {
        Account account = findAccountById(id);
        account.setStatus(status);
        return AccountResponse.from(accountRepository.save(account));
    }

    @Transactional
    @CacheEvict(value = "account", key = "#id")
    public void delete(Long id) {
        if (!accountRepository.existsById(id)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Account not found: " + id);
        }
        accountRepository.deleteById(id);
    }

    /**
     * DEBIT — trừ tiền tài khoản nguồn (dùng trong Saga STEP 2)
     * Có lock bảo vệ race condition (optimistic @Version trên entity)
     */
    @Transactional
    public BalanceUpdateResponse debit(String accountNo, BigDecimal amount, String transactionRef) {
        Account account = findActiveAccountByNumber(accountNo);

        if (account.getBalance().compareTo(amount) < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Insufficient balance. Available: " + account.getBalance() + ", Required: " + amount);
        }

        BigDecimal balanceBefore = account.getBalance();
        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);

        log.info("[SAGA] DEBIT {} from account {} | ref={} | balance: {} → {}",
                amount, accountNo, transactionRef, balanceBefore, account.getBalance());

        return new BalanceUpdateResponse(accountNo, "DEBIT", amount, balanceBefore, account.getBalance(), transactionRef);
    }

    /**
     * CREDIT — cộng tiền tài khoản đích (dùng trong Saga STEP 3)
     */
    @Transactional
    public BalanceUpdateResponse credit(String accountNo, BigDecimal amount, String transactionRef) {
        Account account = findActiveAccountByNumber(accountNo);

        BigDecimal balanceBefore = account.getBalance();
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        log.info("[SAGA] CREDIT {} to account {} | ref={} | balance: {} → {}",
                amount, accountNo, transactionRef, balanceBefore, account.getBalance());

        return new BalanceUpdateResponse(accountNo, "CREDIT", amount, balanceBefore, account.getBalance(), transactionRef);
    }

    /**
     * REVERSE DEBIT — hoàn tiền về tài khoản nguồn (Saga Compensating Transaction)
     */
    @Transactional
    public void reverseDebit(String accountNo, BigDecimal amount, String transactionRef) {
        Account account = findAccountByNumber(accountNo);
        BigDecimal before = account.getBalance();
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
        log.warn("[SAGA COMPENSATE] REVERSE DEBIT {} on account {} | ref={} | balance: {} → {}",
                amount, accountNo, transactionRef, before, account.getBalance());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Account findAccountById(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Account not found: " + id));
    }

    private Account findAccountByNumber(String accountNo) {
        return accountRepository.findByAccountNumber(accountNo)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Account not found: " + accountNo));
    }

    private Account findActiveAccountByNumber(String accountNo) {
        Account account = findAccountByNumber(accountNo);
        if (account.getStatus() != Account.Status.ACTIVE) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Account is not active: " + accountNo);
        }
        return account;
    }

    private String generateAccountNumber() {
        // Format: RK + 10 chữ số ngẫu nhiên
        return "RK" + String.format("%010d", (long)(Math.random() * 10_000_000_000L));
    }
}
