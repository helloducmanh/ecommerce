// src/main/java/com/shopnow/domain/model/CouponRedemption.java
package com.shopnow.domain.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "coupons_used",
        uniqueConstraints = @UniqueConstraint(columnNames = {"promotion_id", "user_id"}))
public class CouponRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id", nullable = false)
    private Promotion promotion;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "used_at", nullable = false)
    private LocalDateTime usedAt = LocalDateTime.now();

    protected CouponRedemption() {
    }

    public CouponRedemption(Promotion promotion, Long userId, Long orderId) {
        this.promotion = promotion;
        this.userId = userId;
        this.orderId = orderId;
    }

    public Long getId() { return id; }
    public Promotion getPromotion() { return promotion; }
    public Long getUserId() { return userId; }
    public Long getOrderId() { return orderId; }
    public LocalDateTime getUsedAt() { return usedAt; }
}
