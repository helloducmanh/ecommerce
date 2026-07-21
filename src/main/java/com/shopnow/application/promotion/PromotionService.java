// src/main/java/com/shopnow/application/promotion/PromotionService.java
package com.shopnow.application.promotion;

import com.shopnow.domain.model.CouponRedemption;
import com.shopnow.domain.model.Promotion;
import com.shopnow.domain.model.PromotionException;
import com.shopnow.domain.port.CouponRedemptionRepository;
import com.shopnow.domain.port.PromotionRepository;
import com.shopnow.presentation.dto.CreatePromotionRequest;
import com.shopnow.presentation.dto.PromotionDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PromotionService {

    private final PromotionRepository promotionRepository;
    private final CouponRedemptionRepository couponRedemptionRepository;

    public PromotionService(PromotionRepository promotionRepository,
                            CouponRedemptionRepository couponRedemptionRepository) {
        this.promotionRepository = promotionRepository;
        this.couponRedemptionRepository = couponRedemptionRepository;
    }

    public record DiscountResult(BigDecimal discountAmount, Promotion promotion) {
    }

    /**
     * Validate a coupon for the given user + cart subtotal and return the discount.
     * Locks the promotion row (caller's transaction) for a consistent usage_count read.
     * Does NOT record the redemption or mutate usage_count — call recordRedemption after the order is saved.
     */
    public DiscountResult validateAndApply(String code, Long userId, BigDecimal cartSubtotal) {
        if (code == null || code.isBlank()) {
            return new DiscountResult(BigDecimal.ZERO, null);
        }
        Promotion promotion = promotionRepository.findByCode(code)
                .orElseThrow(() -> new PromotionException(PromotionException.Code.NOT_FOUND, "Promotion not found: " + code));
        Promotion locked = promotionRepository.findByIdForUpdate(promotion.getId())
                .orElseThrow(() -> new PromotionException(PromotionException.Code.NOT_FOUND, "Promotion not found: " + code));

        if (locked.getStatus() != Promotion.PromoStatus.ACTIVE) {
            throw new PromotionException(PromotionException.Code.INACTIVE, "Promotion is inactive");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(locked.getStartsAt()) || now.isAfter(locked.getEndsAt())) {
            throw new PromotionException(PromotionException.Code.EXPIRED, "Promotion is not within its active window");
        }
        if (locked.getMinOrderValue() != null && cartSubtotal.compareTo(locked.getMinOrderValue()) < 0) {
            throw new PromotionException(PromotionException.Code.MIN_NOT_MET, "Cart subtotal below minimum order value");
        }
        if (locked.getUsageLimit() != null && locked.getUsageCount() >= locked.getUsageLimit()) {
            throw new PromotionException(PromotionException.Code.USAGE_EXCEEDED, "Promotion usage limit reached");
        }
        if (couponRedemptionRepository.existsByPromotionIdAndUserId(locked.getId(), userId)) {
            throw new PromotionException(PromotionException.Code.ALREADY_USED, "You have already used this promotion");
        }
        return new DiscountResult(computeDiscount(locked, cartSubtotal), locked);
    }

    private BigDecimal computeDiscount(Promotion promotion, BigDecimal subtotal) {
        BigDecimal discount;
        if (promotion.getType() == Promotion.PromoType.PERCENTAGE) {
            discount = subtotal.multiply(promotion.getValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else {
            discount = promotion.getValue();
        }
        if (discount.compareTo(subtotal) > 0) {
            discount = subtotal;
        }
        if (discount.signum() < 0) {
            discount = BigDecimal.ZERO;
        }
        return discount;
    }

    @Transactional
    public void recordRedemption(Promotion promotion, Long userId, Long orderId) {
        couponRedemptionRepository.save(new CouponRedemption(promotion, userId, orderId));
        promotion.setUsageCount((promotion.getUsageCount() == null ? 0 : promotion.getUsageCount()) + 1);
        promotionRepository.save(promotion);
    }

    @Transactional
    public void reverseRedemption(Long orderId) {
        couponRedemptionRepository.findByOrderId(orderId).ifPresent(redemption -> {
            couponRedemptionRepository.deleteById(redemption.getId());
            Promotion promotion = redemption.getPromotion();
            promotion.setUsageCount(Math.max(0, (promotion.getUsageCount() == null ? 0 : promotion.getUsageCount()) - 1));
            promotionRepository.save(promotion);
        });
    }

    // --- admin CRUD methods unchanged from Task 1 ---
    @Transactional
    public PromotionDto create(CreatePromotionRequest request) {
        validate(request);
        Promotion promotion = new Promotion(
                request.code(), request.type(), request.value(), request.minOrderValue(),
                request.usageLimit(), request.startsAt(), request.endsAt(), request.status());
        return toDto(promotionRepository.save(promotion));
    }

    @Transactional
    public PromotionDto update(Long id, CreatePromotionRequest request) {
        validate(request);
        Promotion existing = promotionRepository.findById(id)
                .orElseThrow(() -> new PromotionException(PromotionException.Code.NOT_FOUND, "Promotion not found"));
        Promotion updated = new Promotion(
                request.code(), request.type(), request.value(), request.minOrderValue(),
                request.usageLimit(), request.startsAt(), request.endsAt(), request.status());
        try {
            var idField = Promotion.class.getDeclaredField("id"); idField.setAccessible(true); idField.set(updated, existing.getId());
            var usageField = Promotion.class.getDeclaredField("usageCount"); usageField.setAccessible(true); usageField.set(updated, existing.getUsageCount());
        } catch (Exception e) { throw new IllegalStateException(e); }
        return toDto(promotionRepository.save(updated));
    }

    @Transactional(readOnly = true)
    public PromotionDto get(Long id) {
        return toDto(promotionRepository.findById(id)
                .orElseThrow(() -> new PromotionException(PromotionException.Code.NOT_FOUND, "Promotion not found")));
    }

    @Transactional(readOnly = true)
    public List<PromotionDto> list() {
        return promotionRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional
    public void delete(Long id) {
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new PromotionException(PromotionException.Code.NOT_FOUND, "Promotion not found"));
        if (promotion.getUsageCount() != null && promotion.getUsageCount() > 0) {
            throw new PromotionException(PromotionException.Code.USAGE_EXCEEDED,
                    "Cannot delete a promotion that has been redeemed; set status=INACTIVE instead");
        }
        promotionRepository.deleteById(id);
    }

    private void validate(CreatePromotionRequest request) {
        if (request.endsAt().isBefore(request.startsAt())) {
            throw new PromotionException(PromotionException.Code.INVALID_VALUE, "endsAt must be after startsAt");
        }
        if (request.type() == Promotion.PromoType.PERCENTAGE) {
            double v = request.value().doubleValue();
            if (v < 1 || v > 100) {
                throw new PromotionException(PromotionException.Code.INVALID_VALUE,
                        "PERCENTAGE value must be between 1 and 100");
            }
        }
    }

    private PromotionDto toDto(Promotion p) {
        return new PromotionDto(p.getId(), p.getCode(), p.getType().name(), p.getValue(),
                p.getMinOrderValue(), p.getUsageLimit(), p.getUsageCount(),
                p.getStartsAt(), p.getEndsAt(), p.getStatus().name());
    }
}
