package com.shopnow.presentation.dto;

import java.math.BigDecimal;

public record ProductVariantDto(
    Long id,
    String sku,
    BigDecimal price,
    String variantName
) {}
