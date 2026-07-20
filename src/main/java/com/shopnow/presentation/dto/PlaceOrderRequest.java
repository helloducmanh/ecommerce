package com.shopnow.presentation.dto;

import jakarta.validation.constraints.NotNull;

public record PlaceOrderRequest(
    @NotNull Long userId
) {}
