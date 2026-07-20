package com.shopnow.presentation.api;

import com.shopnow.application.order.OrderService;
import com.shopnow.presentation.dto.OrderDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderDto> placeOrder(@RequestParam Long userId) {
        OrderDto order = orderService.placeOrder(userId);
        return ResponseEntity.status(201).body(order);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDto> getOrder(@PathVariable Long id) {
        OrderDto order = orderService.getOrder(id);
        return ResponseEntity.ok(order);
    }

    @GetMapping
    public ResponseEntity<List<OrderDto>> getUserOrders(@RequestParam Long userId) {
        List<OrderDto> orders = orderService.getUserOrders(userId);
        return ResponseEntity.ok(orders);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderDto> cancelOrder(@PathVariable Long id) {
        OrderDto order = orderService.cancelOrder(id);
        return ResponseEntity.ok(order);
    }
}
