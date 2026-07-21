// src/main/java/com/shopnow/domain/port/CouponRedemptionRepository.java
package com.shopnow.domain.port;

import com.shopnow.domain.model.CouponRedemption;

import java.util.Optional;

public interface CouponRedemptionRepository {
    CouponRedemption save(CouponRedemption redemption);
    boolean existsByPromotionIdAndUserId(Long promotionId, Long userId);
    Optional<CouponRedemption> findByOrderId(Long orderId);
    void deleteById(Long id);
}
