package com.shopnow.presentation.dto;

import java.math.BigDecimal;
import java.util.List;

public record OrderDto(
    Long id,
    Long userId,
    String status,
    BigDecimal totalAmount,
    List<OrderItemDto> items
) {}
