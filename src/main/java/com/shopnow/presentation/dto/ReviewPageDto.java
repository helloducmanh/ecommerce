package com.shopnow.presentation.dto;

import java.math.BigDecimal;
import java.util.List;

public record ReviewPageDto(
        Long productId,
        BigDecimal avgRating,
        Integer reviewCount,
        List<ReviewDto> reviews,
        Long totalElements,
        Integer totalPages,
        Integer page
) {
}
