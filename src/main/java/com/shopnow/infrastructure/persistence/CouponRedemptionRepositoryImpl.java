// src/main/java/com/shopnow/infrastructure/persistence/CouponRedemptionRepositoryImpl.java
package com.shopnow.infrastructure.persistence;

import com.shopnow.domain.model.CouponRedemption;
import com.shopnow.domain.port.CouponRedemptionRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class CouponRedemptionRepositoryImpl implements CouponRedemptionRepository {

    private final CouponRedemptionJpaRepository jpaRepository;

    public CouponRedemptionRepositoryImpl(CouponRedemptionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public CouponRedemption save(CouponRedemption redemption) {
        return jpaRepository.save(redemption);
    }

    @Override
    public boolean existsByPromotionIdAndUserId(Long promotionId, Long userId) {
        return jpaRepository.existsByPromotionIdAndUserId(promotionId, userId);
    }

    @Override
    public Optional<CouponRedemption> findByOrderId(Long orderId) {
        return jpaRepository.findByOrderId(orderId);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }
}
