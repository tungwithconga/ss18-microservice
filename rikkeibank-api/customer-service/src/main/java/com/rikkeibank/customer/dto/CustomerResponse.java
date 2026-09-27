package com.rikkeibank.customer.dto;

import com.rikkeibank.customer.entity.Customer;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CustomerResponse {
    private Long id;
    private String fullName;
    private String email;
    private String phone;
    private String nationalId;
    private String address;
    private LocalDate dateOfBirth;
    private String status;
    private LocalDateTime createdAt;

    public static CustomerResponse from(Customer c) {
        return CustomerResponse.builder()
                .id(c.getId())
                .fullName(c.getFullName())
                .email(c.getEmail())
                .phone(c.getPhone())
                .nationalId(c.getNationalId())
                .address(c.getAddress())
                .dateOfBirth(c.getDateOfBirth())
                .status(c.getStatus().name())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
