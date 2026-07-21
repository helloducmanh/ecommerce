// src/main/java/com/shopnow/presentation/dto/PromotionDto.java
package com.shopnow.presentation.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PromotionDto(
        Long id,
        String code,
        String type,
        BigDecimal value,
        BigDecimal minOrderValue,
        Integer usageLimit,
        Integer usageCount,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String status
) {
}
