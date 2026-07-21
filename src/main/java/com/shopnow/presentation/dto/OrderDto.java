// src/main/java/com/shopnow/presentation/dto/OrderDto.java
package com.shopnow.presentation.dto;

import java.math.BigDecimal;
import java.util.List;

public record OrderDto(
    Long id,
    Long userId,
    String status,
    BigDecimal totalAmount,
    BigDecimal discountAmount,
    List<OrderItemDto> items
) {}
