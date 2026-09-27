package com.rikkeibank.account.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class AccountRequest {
    @NotNull private Long customerId;
    @NotNull private Long accountTypeId;
    private BigDecimal initialBalance;
    private String currency;
}
