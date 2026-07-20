package com.shopnow.domain.model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class CartTest {

    @Test
    void shouldAddItemToCart() {
        Cart cart = new Cart(1L);
        cart.addItem(100L, "SKU-001", new BigDecimal("999.00"), 1);

        assertEquals(1, cart.getItems().size());
        assertEquals(new BigDecimal("999.00"), cart.getTotal());
    }

    @Test
    void shouldUpdateItemQuantity() {
        Cart cart = new Cart(1L);
        cart.addItem(100L, "SKU-001", new BigDecimal("999.00"), 1);
        cart.updateItemQuantity(100L, 3);

        assertEquals(3, cart.getItems().get(0).getQuantity());
        assertEquals(new BigDecimal("2997.00"), cart.getTotal());
    }

    @Test
    void shouldRemoveItemFromCart() {
        Cart cart = new Cart(1L);
        cart.addItem(100L, "SKU-001", new BigDecimal("999.00"), 1);
        cart.removeItem(100L);

        assertTrue(cart.getItems().isEmpty());
        assertEquals(BigDecimal.ZERO, cart.getTotal());
    }
}
