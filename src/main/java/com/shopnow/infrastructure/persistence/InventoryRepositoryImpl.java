package com.shopnow.infrastructure.persistence;

import com.shopnow.domain.model.Inventory;
import com.shopnow.domain.port.InventoryRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class InventoryRepositoryImpl implements InventoryRepository {

    private final InventoryJpaRepository jpaRepository;

    public InventoryRepositoryImpl(InventoryJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Inventory save(Inventory inventory) {
        return jpaRepository.save(inventory);
    }

    @Override
    public Optional<Inventory> findByVariantId(Long variantId) {
        return jpaRepository.findByVariant_Id(variantId);
    }

    @Override
    public Optional<Inventory> findByVariantIdForUpdate(Long variantId) {
        return jpaRepository.findByVariantIdForUpdate(variantId);
    }

    @Override
    public List<Inventory> findLowStock() {
        return jpaRepository.findLowStock();
    }
}
