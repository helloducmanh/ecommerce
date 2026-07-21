package com.shopnow.presentation.dto;

import java.time.LocalDateTime;

public record ReviewDto(
        Long id,
        Long productId,
        Long userId,
        String userName,
        Integer rating,
        String comment,
        Boolean verifiedPurchase,
        LocalDateTime createdAt
) {
}
