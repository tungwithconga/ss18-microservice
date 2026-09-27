package com.rikkeibank.account.dto;

import com.rikkeibank.account.entity.Account;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AccountResponse {
    private Long id;
    private String accountNumber;
    private Long customerId;
    private Long accountTypeId;
    private BigDecimal balance;
    private String currency;
    private String status;
    private LocalDateTime openedAt;

    public static AccountResponse from(Account a) {
        return AccountResponse.builder()
                .id(a.getId())
                .accountNumber(a.getAccountNumber())
                .customerId(a.getCustomerId())
                .accountTypeId(a.getAccountTypeId())
                .balance(a.getBalance())
                .currency(a.getCurrency())
                .status(a.getStatus().name())
                .openedAt(a.getOpenedAt())
                .build();
    }
}
