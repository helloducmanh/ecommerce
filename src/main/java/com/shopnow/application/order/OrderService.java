// src/main/java/com/shopnow/application/order/OrderService.java
package com.shopnow.application.order;

import com.shopnow.application.inventory.InventoryService;
import com.shopnow.application.promotion.PromotionService;
import com.shopnow.domain.model.Cart;
import com.shopnow.domain.model.Order;
import com.shopnow.domain.model.OrderItem;
import com.shopnow.domain.model.ProductVariant;
import com.shopnow.domain.model.Promotion;
import com.shopnow.domain.port.CartRepository;
import com.shopnow.domain.port.OrderRepository;
import com.shopnow.domain.port.ProductVariantRepository;
import com.shopnow.presentation.dto.OrderDto;
import com.shopnow.presentation.dto.OrderItemDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryService inventoryService;
    private final PromotionService promotionService;

    public OrderService(OrderRepository orderRepository,
                        CartRepository cartRepository,
                        ProductVariantRepository productVariantRepository,
                        InventoryService inventoryService,
                        PromotionService promotionService) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.productVariantRepository = productVariantRepository;
        this.inventoryService = inventoryService;
        this.promotionService = promotionService;
    }

    @Transactional
    public OrderDto placeOrder(Long userId, String couponCode) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Cart is empty"));
        if (cart.getItems().isEmpty()) {
            throw new IllegalStateException("Cart is empty");
        }

        BigDecimal subtotal = cart.getTotal();

        // 1. Validate + apply coupon (locks promotion row within this tx)
        PromotionService.DiscountResult discount = promotionService.validateAndApply(couponCode, userId, subtotal);

        // 2. Build order items with real variants (looked up for the FK + snapshots)
        List<OrderItem> items = new ArrayList<>();
        List<InventoryService.StockRequest> stockRequests = new ArrayList<>();
        for (var ci : cart.getItems()) {
            ProductVariant variant = productVariantRepository.findById(ci.getVariantId())
                    .orElseThrow(() -> new IllegalArgumentException("Variant not found: " + ci.getVariantId()));
            items.add(new OrderItem(
                    variant.getProduct().getId(), variant,
                    variant.getProduct().getName(), variant.getVariantName(),
                    ci.getQuantity(), ci.getPrice()));
            stockRequests.add(new InventoryService.StockRequest(ci.getVariantId(), ci.getQuantity()));
        }

        // 3. Reserve + commit stock (locks inventory rows in variantId order)
        inventoryService.commitStock(stockRequests);

        // 4. Persist order with discount
        BigDecimal total = subtotal.subtract(discount.discountAmount());
        Order order = new Order(userId, items, total, discount.discountAmount());
        Order saved = orderRepository.save(order);

        // 5. Record coupon redemption (now that the order has an id)
        if (discount.promotion() != null) {
            promotionService.recordRedemption(discount.promotion(), userId, saved.getId());
        }

        // 6. Clear cart after successful commit
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
        return orderRepository.findByUserId(userId).stream().map(this::toDto).toList();
    }

    @Transactional
    public OrderDto cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        order.cancel();

        // Restore stock for each item
        List<InventoryService.StockRequest> restores = order.getItems().stream()
                .map(i -> new InventoryService.StockRequest(i.getVariantId(), i.getQuantity()))
                .toList();
        inventoryService.restoreStock(restores);

        // Reverse coupon redemption if any
        promotionService.reverseRedemption(orderId);

        Order saved = orderRepository.save(order);
        return toDto(saved);
    }

    private OrderDto toDto(Order order) {
        List<OrderItemDto> itemDtos = order.getItems().stream()
                .map(i -> new OrderItemDto(
                        i.getProductId(), i.getVariantId(),
                        i.getProductName(), i.getVariantName(),
                        i.getQuantity(), i.getUnitPrice(), i.getSubtotal()))
                .toList();
        return new OrderDto(order.getId(), order.getUserId(), order.getStatus().name(),
                order.getTotalAmount(), order.getDiscountAmount(), itemDtos);
    }
}
