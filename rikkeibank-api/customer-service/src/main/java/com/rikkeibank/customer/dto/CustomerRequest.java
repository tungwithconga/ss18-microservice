package com.rikkeibank.customer.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class CustomerRequest {
    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank @Email(message = "Invalid email format")
    private String email;

    private String phone;
    private String nationalId;
    private String address;
    private LocalDate dateOfBirth;
}
