// src/main/java/com/shopnow/presentation/dto/CreatePromotionRequest.java
package com.shopnow.presentation.dto;

import com.shopnow.domain.model.Promotion;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreatePromotionRequest(
        @NotBlank @Size(max = 50) String code,
        @NotNull Promotion.PromoType type,
        @NotNull @DecimalMin("0.01") BigDecimal value,
        @DecimalMin("0") BigDecimal minOrderValue,
        @Min(1) Integer usageLimit,
        @NotNull LocalDateTime startsAt,
        @NotNull LocalDateTime endsAt,
        Promotion.PromoStatus status
) {
}
