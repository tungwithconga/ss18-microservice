package com.rikkeibank.account.dto;

import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class BalanceUpdateResponse {
    private String accountNo;
    private String entryType;       // DEBIT hoặc CREDIT
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private String transactionRef;
}
