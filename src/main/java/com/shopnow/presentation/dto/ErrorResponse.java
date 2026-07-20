package com.shopnow.presentation.dto;

public record ErrorResponse(
        String code,
        String message,
        Object details
) {
}
