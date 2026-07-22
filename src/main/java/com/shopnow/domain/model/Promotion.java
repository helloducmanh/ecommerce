// src/main/java/com/shopnow/domain/model/Promotion.java
package com.shopnow.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "promotions")
public class Promotion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 50)
    private String code;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PromoType type;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal value;
    @Column(name = "min_order_value", precision = 10, scale = 2)
    private BigDecimal minOrderValue;
    @Column(name = "usage_limit")
    private Integer usageLimit;
    @Column(name = "usage_count", nullable = false)
    private Integer usageCount = 0;
    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;
    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PromoStatus status = PromoStatus.INACTIVE;
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    protected Promotion() {
    }

    public Promotion(String code, PromoType type, BigDecimal value, BigDecimal minOrderValue,
                     Integer usageLimit, LocalDateTime startsAt, LocalDateTime endsAt, PromoStatus status) {
        this.code = code == null ? null : code.toUpperCase();
        this.type = type;
        this.value = value;
        this.minOrderValue = minOrderValue;
        this.usageLimit = usageLimit;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = status == null ? PromoStatus.INACTIVE : status;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public PromoType getType() { return type; }
    public BigDecimal getValue() { return value; }
    public BigDecimal getMinOrderValue() { return minOrderValue; }
    public Integer getUsageLimit() { return usageLimit; }
    public Integer getUsageCount() { return usageCount; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public LocalDateTime getEndsAt() { return endsAt; }
    public PromoStatus getStatus() { return status; }

    public void setStatus(PromoStatus status) { this.status = status; }
    public void setUsageCount(Integer usageCount) { this.usageCount = usageCount; }

    public enum PromoType { PERCENTAGE, FIXED }
    public enum PromoStatus { ACTIVE, INACTIVE }
}
