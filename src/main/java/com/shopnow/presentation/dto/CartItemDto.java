package com.shopnow.presentation.dto;

import java.math.BigDecimal;

public record CartItemDto(
    Long variantId,
    String sku,
    BigDecimal price,
    Integer quantity,
    BigDecimal subtotal
) {}
