package com.rikkeibank.transaction.dto;

import com.rikkeibank.transaction.entity.Transaction;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TransactionResponse {
    private Long id;
    private String transactionRef;
    private String sourceAccountNo;
    private String targetAccountNo;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    public static TransactionResponse from(Transaction tx) {
        return TransactionResponse.builder()
                .id(tx.getId())
                .transactionRef(tx.getTransactionRef())
                .sourceAccountNo(tx.getSourceAccountNo())
                .targetAccountNo(tx.getTargetAccountNo())
                .amount(tx.getAmount())
                .currency(tx.getCurrency())
                .description(tx.getDescription())
                .status(tx.getStatus().name())
                .createdAt(tx.getCreatedAt())
                .completedAt(tx.getCompletedAt())
                .build();
    }
}
