package com.shopnow.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ProductTest {

    @Test
    void shouldCreateProduct() {
        Category category = new Category("Electronics", "electronics");
        Product product = new Product("iPhone 15", "iphone-15", category, new BigDecimal("999.00"));

        assertEquals("iPhone 15", product.getName());
        assertEquals("iphone-15", product.getSlug());
        assertEquals(new BigDecimal("999.00"), product.getBasePrice());
    }
}
