package com.shopnow.domain.port;

import com.shopnow.domain.model.Inventory;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository {
    Inventory save(Inventory inventory);
    Optional<Inventory> findByVariantId(Long variantId);
    Optional<Inventory> findByVariantIdForUpdate(Long variantId);
    List<Inventory> findLowStock();
}
