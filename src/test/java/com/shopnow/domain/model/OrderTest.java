package com.shopnow.domain.model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    @Test
    void shouldCreateOrder() {
        OrderItem item = new OrderItem(1L, 100L, "iPhone 15", "128GB/Black", 1, new BigDecimal("999.00"));
        Order order = new Order(1L, List.of(item), new BigDecimal("999.00"));

        assertEquals(1L, order.getUserId());
        assertEquals(Order.OrderStatus.PENDING, order.getStatus());
        assertEquals(new BigDecimal("999.00"), order.getTotalAmount());
    }

    @Test
    void shouldCancelPendingOrder() {
        Order order = new Order(1L, List.of(), BigDecimal.ZERO);
        order.cancel();
        assertEquals(Order.OrderStatus.CANCELLED, order.getStatus());
    }

    @Test
    void shouldRejectCancelNonPendingOrder() {
        Order order = new Order(1L, List.of(), BigDecimal.ZERO);
        order.cancel();
        assertThrows(IllegalStateException.class, () -> order.cancel());
    }
}
