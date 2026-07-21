// src/main/java/com/shopnow/infrastructure/persistence/ProductVariantJpaRepository.java
package com.shopnow.infrastructure.persistence;

import com.shopnow.domain.model.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVariantJpaRepository extends JpaRepository<ProductVariant, Long> {
}
