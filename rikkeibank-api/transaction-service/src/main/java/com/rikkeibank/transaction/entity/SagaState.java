package com.rikkeibank.transaction.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "saga_states")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SagaState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_ref", unique = true, nullable = false, length = 50)
    private String transactionRef;

    @Column(name = "current_step", length = 100)
    private String currentStep;

    @Enumerated(EnumType.STRING)
    private SagaStatus status;

    @Column(columnDefinition = "JSON")
    private String payload;  // JSON string của TransferRequest

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum SagaStatus {
        STARTED, DEBIT_DONE, CREDIT_DONE, COMPLETED, COMPENSATING, FAILED
    }
}
