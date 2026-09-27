package com.rikkeibank.transaction.dto;

import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class SagaOperationRequest {
    private String accountNo;
    private BigDecimal amount;
    private String transactionRef;
}
