// src/main/java/com/shopnow/infrastructure/persistence/ProductVariantRepositoryImpl.java
package com.shopnow.infrastructure.persistence;

import com.shopnow.domain.model.ProductVariant;
import com.shopnow.domain.port.ProductVariantRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class ProductVariantRepositoryImpl implements ProductVariantRepository {

    private final ProductVariantJpaRepository jpaRepository;

    public ProductVariantRepositoryImpl(ProductVariantJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<ProductVariant> findById(Long id) {
        return jpaRepository.findById(id);
    }
}
