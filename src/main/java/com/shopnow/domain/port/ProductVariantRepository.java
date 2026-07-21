// src/main/java/com/shopnow/domain/port/ProductVariantRepository.java
package com.shopnow.domain.port;

import com.shopnow.domain.model.ProductVariant;

import java.util.Optional;

public interface ProductVariantRepository {
    Optional<ProductVariant> findById(Long id);
}
