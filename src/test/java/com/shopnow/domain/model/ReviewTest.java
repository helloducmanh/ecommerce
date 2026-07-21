// src/test/java/com/shopnow/domain/model/ReviewTest.java
package com.shopnow.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ReviewTest {

    @Test
    void shouldCreateReviewWithDefaults() {
        Category category = new Category("Electronics", "electronics");
        Product product = new Product("iPhone 15", "iphone-15", category, new BigDecimal("999.00"));
        Review review = new Review(product, 7L, "Alice Smith", 5, "Great phone");

        assertEquals(product, review.getProduct());
        assertEquals(7L, review.getUserId());
        assertEquals("Alice Smith", review.getUserName());
        assertEquals(5, review.getRating());
        assertEquals("Great phone", review.getComment());
        assertTrue(review.getVerifiedPurchase());
        assertNotNull(review.getCreatedAt());
    }
}
