package com.shopnow.presentation.dto;

public record CategoryDto(
        Long id,
        String name,
        String slug,
        Long parentId,
        Integer depth
) {
}
