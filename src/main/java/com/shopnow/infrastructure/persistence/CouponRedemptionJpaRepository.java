// src/main/java/com/shopnow/infrastructure/persistence/CouponRedemptionJpaRepository.java
package com.shopnow.infrastructure.persistence;

import com.shopnow.domain.model.CouponRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponRedemptionJpaRepository extends JpaRepository<CouponRedemption, Long> {
    boolean existsByPromotionIdAndUserId(Long promotionId, Long userId);
    Optional<CouponRedemption> findByOrderId(Long orderId);
}
