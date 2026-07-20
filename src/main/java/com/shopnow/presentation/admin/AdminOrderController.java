package com.shopnow.presentation.admin;

import com.shopnow.application.order.OrderService;
import com.shopnow.presentation.dto.OrderDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ResponseEntity<List<OrderDto>> getAllOrders() {
        // TODO: Implement pagination
        return ResponseEntity.ok(List.of());
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> updateOrderStatus(
        @PathVariable Long id,
        @RequestParam String status
    ) {
        // TODO: Implement status update
        return ResponseEntity.noContent().build();
    }
}
