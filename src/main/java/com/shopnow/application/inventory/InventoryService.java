package com.shopnow.application.inventory;

import com.shopnow.domain.model.Inventory;
import com.shopnow.domain.model.InsufficientStockException;
import com.shopnow.domain.port.InventoryRepository;
import com.shopnow.presentation.dto.LowStockDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryRepository inventoryRepository;

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    public record StockRequest(Long variantId, Integer quantity) {
    }

    /**
     * Lock, reserve, and commit stock for each item in one transaction. Items are sorted by
     * variantId so all callers lock in the same order (deadlock avoidance). Any shortfall
     * throws InsufficientStockException and the caller's @Transactional rolls back all prior
     * commits in this call.
     */
    @Transactional
    public void commitStock(List<StockRequest> items) {
        List<StockRequest> sorted = items.stream()
                .sorted(Comparator.comparing(StockRequest::variantId))
                .toList();
        for (StockRequest item : sorted) {
            Inventory inventory = inventoryRepository.findByVariantIdForUpdate(item.variantId())
                    .orElseThrow(() -> new InsufficientStockException("No inventory for variant " + item.variantId()));
            try {
                inventory.reserve(item.quantity());      // throws if available < qty
            } catch (IllegalStateException e) {
                throw new InsufficientStockException(
                        "Insufficient stock for variant " + item.variantId());
            }
            inventory.commitReservation(item.quantity());
            if (inventory.getAvailable() < inventory.getThreshold()) {
                log.warn("Low stock for variant {}: available={}, threshold={}",
                        item.variantId(), inventory.getAvailable(), inventory.getThreshold());
            }
            inventoryRepository.save(inventory);
        }
    }

    @Transactional
    public void restoreStock(List<StockRequest> items) {
        List<StockRequest> sorted = items.stream()
                .sorted(Comparator.comparing(StockRequest::variantId))
                .toList();
        for (StockRequest item : sorted) {
            Inventory inventory = inventoryRepository.findByVariantIdForUpdate(item.variantId())
                    .orElseThrow(() -> new IllegalStateException(
                            "No inventory row for variant " + item.variantId()
                                    + " during stock restore (data integrity error)"));
            inventory.restoreCommitted(item.quantity());
            inventoryRepository.save(inventory);
        }
    }

    @Transactional(readOnly = true)
    public List<LowStockDto> lowStock() {
        return inventoryRepository.findLowStock().stream()
                .map(i -> new LowStockDto(
                        i.getVariant().getId(),
                        i.getVariant().getSku(),
                        i.getVariant().getProduct().getName(),
                        i.getQuantity(),
                        i.getReserved(),
                        i.getAvailable(),
                        i.getThreshold()))
                .toList();
    }
}
