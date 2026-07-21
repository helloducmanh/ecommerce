package com.shopnow.presentation.dto;

public record LowStockDto(
        Long variantId,
        String sku,
        String productName,
        Integer quantity,
        Integer reserved,
        Integer available,
        Integer threshold
) {
}
