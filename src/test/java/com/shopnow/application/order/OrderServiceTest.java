package com.shopnow.application.order;

import com.shopnow.domain.model.Cart;
import com.shopnow.domain.model.Order;
import com.shopnow.domain.port.CartRepository;
import com.shopnow.domain.port.OrderRepository;
import com.shopnow.presentation.dto.OrderDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartRepository cartRepository;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, cartRepository);
    }

    @Test
    void shouldPlaceOrder() {
        Cart cart = new Cart(1L);
        cart.addItem(100L, "SKU-001", new BigDecimal("999.00"), 1);

        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderDto result = orderService.placeOrder(1L);

        assertNotNull(result);
        assertEquals(1, result.items().size());
        verify(cartRepository).deleteByUserId(1L);
    }

    @Test
    void shouldThrowWhenCartEmpty() {
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> orderService.placeOrder(1L));
    }

    @Test
    void shouldCancelOrder() {
        Order order = new Order(1L, java.util.List.of(), BigDecimal.ZERO);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderDto result = orderService.cancelOrder(1L);

        assertEquals("CANCELLED", result.status());
    }
}
