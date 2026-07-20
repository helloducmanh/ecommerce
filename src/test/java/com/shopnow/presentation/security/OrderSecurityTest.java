// src/test/java/com/shopnow/presentation/security/OrderSecurityTest.java
package com.shopnow.presentation.security;

import com.shopnow.domain.model.Order;
import com.shopnow.domain.port.OrderRepository;
import com.shopnow.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderSecurityTest {

    @Mock
    private OrderRepository orderRepository;

    private OrderSecurity orderSecurity;

    @BeforeEach
    void setUp() {
        orderSecurity = new OrderSecurity(orderRepository);
    }

    private Authentication authFor(Long userId) {
        UserPrincipal principal = new UserPrincipal(userId, "u@example.com", "CUSTOMER");
        return new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
    }

    @Test
    void shouldAllowOwner() {
        Order order = new Order(1L, List.of(), BigDecimal.ZERO);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        assertTrue(orderSecurity.isOwner(10L, authFor(1L)));
    }

    @Test
    void shouldRejectNonOwner() {
        Order order = new Order(1L, List.of(), BigDecimal.ZERO);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        assertFalse(orderSecurity.isOwner(10L, authFor(999L)));
    }

    @Test
    void shouldRejectWhenOrderMissing() {
        when(orderRepository.findById(10L)).thenReturn(Optional.empty());

        assertFalse(orderSecurity.isOwner(10L, authFor(1L)));
    }

    @Test
    void shouldRejectAnonymousPrincipal() {
        Authentication anonymous = new UsernamePasswordAuthenticationToken("anonymousUser", null, List.of());
        assertFalse(orderSecurity.isOwner(10L, anonymous));
    }
}
