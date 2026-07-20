package com.shopnow.presentation.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProductDto(
    Long id,
    String name,
    String slug,
    String description,
    BigDecimal basePrice,
    String categoryName,
    List<ProductVariantDto> variants
) {}
