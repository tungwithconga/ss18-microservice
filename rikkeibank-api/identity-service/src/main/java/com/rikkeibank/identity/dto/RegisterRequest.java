package com.rikkeibank.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class RegisterRequest {
    @NotBlank
    private String username;

    @NotBlank
    private String password;

    @NotBlank
    private String role;  // "ADMIN" | "TELLER" | "CUSTOMER"

    private Long customerId;
    private Long staffId;
}
