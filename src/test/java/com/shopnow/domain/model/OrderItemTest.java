// src/test/java/com/shopnow/domain/model/OrderItemTest.java
package com.shopnow.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class OrderItemTest {

    @Test
    void shouldExposeVariantIdFromRelation() {
        Category category = new Category("Electronics", "electronics");
        Product product = new Product("iPhone 15", "iphone-15", category, new BigDecimal("999.00"));
        ProductVariant variant = new ProductVariant(product, "SKU-1", new BigDecimal("999.00"));
        // simulate JPA-assigned id
        try {
            var f = ProductVariant.class.getDeclaredField("id"); f.setAccessible(true); f.set(variant, 7L);
        } catch (Exception e) { fail(e); }

        OrderItem item = new OrderItem(1L, variant, "iPhone 15", "128GB", 2, new BigDecimal("999.00"));

        assertEquals(7L, item.getVariantId());
        assertEquals(variant, item.getVariant());
        assertEquals(new BigDecimal("1998.00"), item.getSubtotal());
    }
}
