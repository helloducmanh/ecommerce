// src/test/java/com/shopnow/domain/model/PromotionExceptionTest.java
package com.shopnow.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PromotionExceptionTest {
    @Test
    void shouldCarryCode() {
        PromotionException ex = new PromotionException(PromotionException.Code.EXPIRED, "expired");
        assertEquals(PromotionException.Code.EXPIRED, ex.getCode());
        assertEquals("expired", ex.getMessage());
    }
}
