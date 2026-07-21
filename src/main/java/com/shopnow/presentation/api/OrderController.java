// src/main/java/com/shopnow/presentation/api/OrderController.java
package com.shopnow.presentation.api;

import com.shopnow.application.order.OrderService;
import com.shopnow.infrastructure.security.UserPrincipal;
import com.shopnow.presentation.dto.OrderDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    public ResponseEntity<OrderDto> placeOrder(@AuthenticationPrincipal UserPrincipal principal,
                                               @RequestParam(required = false) String couponCode) {
        return ResponseEntity.status(201).body(orderService.placeOrder(principal.userId(), couponCode));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@orderSecurity.isOwner(#id, authentication)")
    public ResponseEntity<OrderDto> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrder(id));
    }

    @GetMapping
    public ResponseEntity<List<OrderDto>> getUserOrders(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(orderService.getUserOrders(principal.userId()));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@orderSecurity.isOwner(#id, authentication)")
    public ResponseEntity<OrderDto> cancelOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.cancelOrder(id));
    }
}
