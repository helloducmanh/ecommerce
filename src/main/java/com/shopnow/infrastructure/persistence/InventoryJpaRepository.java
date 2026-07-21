package com.shopnow.infrastructure.persistence;

import com.shopnow.domain.model.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InventoryJpaRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByVariant_Id(Long variantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.variant.id = :variantId")
    Optional<Inventory> findByVariantIdForUpdate(@Param("variantId") Long variantId);

    @Query("SELECT i FROM Inventory i WHERE (i.quantity - i.reserved) < i.threshold")
    List<Inventory> findLowStock();
}
