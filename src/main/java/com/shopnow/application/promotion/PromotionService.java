// src/main/java/com/shopnow/application/promotion/PromotionService.java
package com.shopnow.application.promotion;

import com.shopnow.domain.model.Promotion;
import com.shopnow.domain.model.PromotionException;
import com.shopnow.domain.port.PromotionRepository;
import com.shopnow.presentation.dto.CreatePromotionRequest;
import com.shopnow.presentation.dto.PromotionDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PromotionService {
    private final PromotionRepository promotionRepository;

    public PromotionService(PromotionRepository promotionRepository) {
        this.promotionRepository = promotionRepository;
    }

    @Transactional
    public PromotionDto create(CreatePromotionRequest request) {
        validate(request);
        Promotion promotion = new Promotion(
                request.code(),
                request.type(),
                request.value(),
                request.minOrderValue(),
                request.usageLimit(),
                request.startsAt(),
                request.endsAt(),
                request.status());
        return toDto(promotionRepository.save(promotion));
    }

    @Transactional
    public PromotionDto update(Long id, CreatePromotionRequest request) {
        validate(request);
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new PromotionException(PromotionException.Code.NOT_FOUND, "Promotion not found"));
        Promotion updated = new Promotion(
                request.code(),
                request.type(),
                request.value(),
                request.minOrderValue(),
                request.usageLimit(),
                request.startsAt(),
                request.endsAt(),
                request.status());
        try {
            var f = Promotion.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(updated, promotion.getId());
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
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
        return new PromotionDto(
                p.getId(),
                p.getCode(),
                p.getType().name(),
                p.getValue(),
                p.getMinOrderValue(),
                p.getUsageLimit(),
                p.getUsageCount(),
                p.getStartsAt(),
                p.getEndsAt(),
                p.getStatus().name());
    }
}
