package com.rikkeibank.transaction.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class TransferRequest {

    @NotBlank(message = "Source account number is required")
    private String sourceAccountNo;

    @NotBlank(message = "Target account number is required")
    private String targetAccountNo;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1000", message = "Minimum transfer amount is 1,000 VND")
    private BigDecimal amount;

    private String currency;
    private String description;
}
