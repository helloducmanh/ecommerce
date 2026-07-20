package com.shopnow.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 100)
    private String provider;

    @Column(length = 255)
    private String providerTransactionId;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected Payment() {}

    public Payment(Long orderId, BigDecimal amount) {
        this.orderId = orderId;
        this.amount = amount;
    }

    public void markCompleted(String provider, String transactionId) {
        this.provider = provider;
        this.providerTransactionId = transactionId;
        this.status = PaymentStatus.COMPLETED;
    }

    public void markFailed() {
        this.status = PaymentStatus.FAILED;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public PaymentStatus getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public String getProvider() { return provider; }
    public String getProviderTransactionId() { return providerTransactionId; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public enum PaymentStatus {
        PENDING, COMPLETED, FAILED, REFUNDED
    }
}
