package com.rikkeibank.transaction.event;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class TransferSuccessEvent {
    private String transactionRef;
    private String sourceAccountNo;
    private String targetAccountNo;
    private BigDecimal amount;
    private LocalDateTime completedAt;
}
