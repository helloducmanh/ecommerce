package com.shopnow.infrastructure.persistence;

import com.shopnow.domain.model.Order;
import com.shopnow.domain.port.OrderQueryPort;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class OrderQueryAdapter implements OrderQueryPort {
    private static final List<String> PURCHASED_STATUSES = List.of(
            Order.OrderStatus.CONFIRMED.name(),
            Order.OrderStatus.SHIPPED.name(),
            Order.OrderStatus.DELIVERED.name()
    );

    private final OrderJpaRepository orderJpaRepository;

    public OrderQueryAdapter(OrderJpaRepository orderJpaRepository) {
        this.orderJpaRepository = orderJpaRepository;
    }

    @Override
    public boolean hasUserPurchasedProduct(Long userId, Long productId) {
        return orderJpaRepository.existsPurchaseOfProduct(userId, productId, PURCHASED_STATUSES);
    }
}
