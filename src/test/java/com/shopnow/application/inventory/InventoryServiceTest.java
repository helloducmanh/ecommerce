package com.shopnow.application.inventory;

import com.shopnow.domain.model.InsufficientStockException;
import com.shopnow.domain.model.Inventory;
import com.shopnow.domain.port.InventoryRepository;
import com.shopnow.presentation.dto.LowStockDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService(inventoryRepository);
    }

    @Test
    void shouldCommitStockWhenAvailable() {
        Inventory inv = new Inventory(null, 10); // variant null here; service only calls reserve/commit/save
        when(inventoryRepository.findByVariantIdForUpdate(1L)).thenReturn(Optional.of(inv));

        inventoryService.commitStock(List.of(new InventoryService.StockRequest(1L, 3)));

        assertEquals(7, inv.getQuantity());
        verify(inventoryRepository).save(inv);
    }

    @Test
    void shouldThrowWhenInsufficient() {
        Inventory inv = new Inventory(null, 2);
        when(inventoryRepository.findByVariantIdForUpdate(1L)).thenReturn(Optional.of(inv));

        assertThrows(InsufficientStockException.class,
                () -> inventoryService.commitStock(List.of(new InventoryService.StockRequest(1L, 5))));
        // quantity unchanged because reserve() threw before commit
        assertEquals(2, inv.getQuantity());
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenNoInventoryForVariant() {
        when(inventoryRepository.findByVariantIdForUpdate(99L)).thenReturn(Optional.empty());
        assertThrows(InsufficientStockException.class,
                () -> inventoryService.commitStock(List.of(new InventoryService.StockRequest(99L, 1))));
    }

    @Test
    void shouldRestoreStock() {
        Inventory inv = new Inventory(null, 5);
        when(inventoryRepository.findByVariantIdForUpdate(1L)).thenReturn(Optional.of(inv));

        inventoryService.restoreStock(List.of(new InventoryService.StockRequest(1L, 3)));

        assertEquals(8, inv.getQuantity());
        verify(inventoryRepository).save(inv);
    }

    @Test
    void shouldThrowDataIntegrityErrorWhenNoInventoryToRestore() {
        when(inventoryRepository.findByVariantIdForUpdate(99L)).thenReturn(Optional.empty());

        // Restoring stock for an order item with no inventory row is a data-integrity error,
        // not a customer-facing "insufficient stock" 409 — so it surfaces as IllegalStateException (500).
        assertThrows(IllegalStateException.class,
                () -> inventoryService.restoreStock(List.of(new InventoryService.StockRequest(99L, 1))));
    }

    @Test
    void lowStockDelegatesToRepository() {
        // lowStock mapping is exercised in the controller/repository test; here just verify delegation wiring
        when(inventoryRepository.findLowStock()).thenReturn(List.of());
        List<LowStockDto> result = inventoryService.lowStock();
        assertNotNull(result);
        assertEquals(0, result.size());
    }
}
