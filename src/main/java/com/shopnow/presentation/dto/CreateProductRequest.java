package com.shopnow.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateProductRequest(
    @NotBlank String name,
    @NotBlank String slug,
    String description,
    @NotNull Long categoryId,
    @NotNull BigDecimal basePrice
) {}
