package com.shopnow.application.order;

import com.shopnow.domain.model.Cart;
import com.shopnow.domain.model.Order;
import com.shopnow.domain.model.OrderItem;
import com.shopnow.domain.port.CartRepository;
import com.shopnow.domain.port.OrderRepository;
import com.shopnow.presentation.dto.OrderDto;
import com.shopnow.presentation.dto.OrderItemDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;

    public OrderService(OrderRepository orderRepository, CartRepository cartRepository) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
    }

    @Transactional
    public OrderDto placeOrder(Long userId) {
        Cart cart = cartRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("Cart is empty"));

        if (cart.getItems().isEmpty()) {
            throw new IllegalStateException("Cart is empty");
        }

        List<OrderItem> items = cart.getItems().stream()
            .map(ci -> new OrderItem(
                ci.getVariantId(), ci.getVariantId(),
                "Product-" + ci.getVariantId(), ci.getSku(),
                ci.getQuantity(), ci.getPrice()
            ))
            .toList();

        Order order = new Order(userId, items, cart.getTotal());
        Order saved = orderRepository.save(order);

        cartRepository.deleteByUserId(userId);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public OrderDto getOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        return toDto(order);
    }

    @Transactional(readOnly = true)
    public List<OrderDto> getUserOrders(Long userId) {
        return orderRepository.findByUserId(userId).stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional
    public OrderDto cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        order.cancel();
        Order saved = orderRepository.save(order);
        return toDto(saved);
    }

    private OrderDto toDto(Order order) {
        List<OrderItemDto> items = order.getItems().stream()
            .map(i -> new OrderItemDto(
                i.getProductId(), i.getVariantId(),
                i.getProductName(), i.getVariantName(),
                i.getQuantity(), i.getUnitPrice(), i.getSubtotal()
            ))
            .toList();
        return new OrderDto(order.getId(), order.getUserId(),
            order.getStatus().name(), order.getTotalAmount(), items);
    }
}
