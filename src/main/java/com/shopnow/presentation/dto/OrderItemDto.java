package com.shopnow.presentation.dto;

import java.math.BigDecimal;

public record OrderItemDto(
    Long productId,
    Long variantId,
    String productName,
    String variantName,
    Integer quantity,
    BigDecimal unitPrice,
    BigDecimal subtotal
) {}
