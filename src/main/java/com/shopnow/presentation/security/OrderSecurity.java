// src/main/java/com/shopnow/presentation/security/OrderSecurity.java
package com.shopnow.presentation.security;

import com.shopnow.domain.model.Order;
import com.shopnow.domain.port.OrderRepository;
import com.shopnow.infrastructure.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component("orderSecurity")
public class OrderSecurity {

    private final OrderRepository orderRepository;

    public OrderSecurity(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public boolean isOwner(Long orderId, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return false;
        }
        Optional<Order> order = orderRepository.findById(orderId);
        return order.map(o -> o.getUserId().equals(principal.userId())).orElse(false);
    }
}
