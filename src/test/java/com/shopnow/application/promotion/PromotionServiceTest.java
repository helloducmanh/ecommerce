// src/test/java/com/shopnow/application/promotion/PromotionServiceTest.java
package com.shopnow.application.promotion;

import com.shopnow.domain.model.Promotion;
import com.shopnow.domain.model.PromotionException;
import com.shopnow.domain.port.PromotionRepository;
import com.shopnow.presentation.dto.CreatePromotionRequest;
import com.shopnow.presentation.dto.PromotionDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PromotionServiceTest {
    @Mock
    private PromotionRepository promotionRepository;
    @Mock
    private com.shopnow.domain.port.CouponRedemptionRepository couponRedemptionRepository;
    private PromotionService promotionService;

    private final LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
    private final LocalDateTime end = LocalDateTime.of(2026, 12, 31, 23, 59);

    @BeforeEach
    void setUp() {
        promotionService = new PromotionService(promotionRepository, couponRedemptionRepository);
    }

    private CreatePromotionRequest req(Promotion.PromoType type, BigDecimal value) {
        return new CreatePromotionRequest("summer20", type, value, null, 100, start, end, Promotion.PromoStatus.ACTIVE);
    }

    @Test
    void shouldCreatePercentagePromotionAndUppercaseCode() {
        when(promotionRepository.save(any(Promotion.class))).thenAnswer(inv -> {
            Promotion p = inv.getArgument(0);
            var f = Promotion.class.getDeclaredField("id"); f.setAccessible(true); f.set(p, 1L);
            return p;
        });
        PromotionDto dto = promotionService.create(req(Promotion.PromoType.PERCENTAGE, new BigDecimal("20")));
        assertEquals("SUMMER20", dto.code());
        assertEquals("PERCENTAGE", dto.type());
        assertEquals("ACTIVE", dto.status());
    }

    @Test
    void shouldRejectPercentageValueOutOfRange() {
        assertThrows(PromotionException.class,
                () -> promotionService.create(req(Promotion.PromoType.PERCENTAGE, new BigDecimal("150"))));
        assertThrows(PromotionException.class,
                () -> promotionService.create(req(Promotion.PromoType.PERCENTAGE, BigDecimal.ZERO)));
    }

    @Test
    void shouldRejectEndsAtBeforeStartsAt() {
        CreatePromotionRequest bad = new CreatePromotionRequest(
                "x", Promotion.PromoType.FIXED, new BigDecimal("5"), null, null, end, start, Promotion.PromoStatus.ACTIVE);
        PromotionException ex = assertThrows(PromotionException.class, () -> promotionService.create(bad));
        assertEquals(PromotionException.Code.INVALID_VALUE, ex.getCode());
    }

    @Test
    void shouldListPromotions() {
        when(promotionRepository.findAll()).thenReturn(List.of());
        assertEquals(0, promotionService.list().size());
    }

    @Test
    void shouldGetById() {
        Promotion p = new Promotion("X", Promotion.PromoType.FIXED, new BigDecimal("5"), null, null, start, end, Promotion.PromoStatus.ACTIVE);
        when(promotionRepository.findById(1L)).thenReturn(Optional.of(p));
        assertEquals("X", promotionService.get(1L).code());
    }

    @Test
    void shouldThrowNotFoundOnMissingGet() {
        when(promotionRepository.findById(9L)).thenReturn(Optional.empty());
        PromotionException ex = assertThrows(PromotionException.class, () -> promotionService.get(9L));
        assertEquals(PromotionException.Code.NOT_FOUND, ex.getCode());
    }

    @Test
    void shouldDeleteUnusedPromotion() {
        Promotion p = new Promotion("X", Promotion.PromoType.FIXED, new BigDecimal("5"), null, null, start, end, Promotion.PromoStatus.ACTIVE);
        when(promotionRepository.findById(1L)).thenReturn(Optional.of(p));
        promotionService.delete(1L);
        verify(promotionRepository).deleteById(1L);
    }

    @Test
    void shouldNotDeleteRedeemedPromotion() {
        Promotion p = new Promotion("X", Promotion.PromoType.FIXED, new BigDecimal("5"), null, null, start, end, Promotion.PromoStatus.ACTIVE);
        p.setUsageCount(3);
        when(promotionRepository.findById(1L)).thenReturn(Optional.of(p));
        PromotionException ex = assertThrows(PromotionException.class, () -> promotionService.delete(1L));
        assertEquals(PromotionException.Code.USAGE_EXCEEDED, ex.getCode());
        verify(promotionRepository, never()).deleteById(any());
    }

    @Test
    void shouldValidateAndApplyPercentageDiscount() {
        Promotion promo = new Promotion("SUMMER20", Promotion.PromoType.PERCENTAGE, new BigDecimal("20"),
                null, null, start, end, Promotion.PromoStatus.ACTIVE);
        when(promotionRepository.findByCode("SUMMER20")).thenReturn(Optional.of(promo));
        when(promotionRepository.findByIdForUpdate(any())).thenReturn(Optional.of(promo));
        when(couponRedemptionRepository.existsByPromotionIdAndUserId(any(), any())).thenReturn(false);

        PromotionService.DiscountResult result = promotionService.validateAndApply("SUMMER20", 1L, new BigDecimal("100.00"));

        assertEquals(new BigDecimal("20.00"), result.discountAmount());
        assertEquals(promo, result.promotion());
    }

    @Test
    void shouldReturnZeroDiscountWhenNoCode() {
        PromotionService.DiscountResult result = promotionService.validateAndApply(null, 1L, new BigDecimal("100.00"));
        assertEquals(BigDecimal.ZERO, result.discountAmount());
        assertNull(result.promotion());
    }
}
