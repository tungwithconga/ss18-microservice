package com.rikkeibank.notification.event;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class TransferFailedEvent {
    private String transactionRef;
    private String sourceAccountNo;
    private String targetAccountNo;
    private BigDecimal amount;
    private String reason;
    private LocalDateTime failedAt;
}
