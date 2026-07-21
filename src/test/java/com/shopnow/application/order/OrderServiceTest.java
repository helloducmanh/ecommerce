// src/test/java/com/shopnow/application/order/OrderServiceTest.java
package com.shopnow.application.order;

import com.shopnow.application.inventory.InventoryService;
import com.shopnow.application.promotion.PromotionService;
import com.shopnow.domain.model.Cart;
import com.shopnow.domain.model.Category;
import com.shopnow.domain.model.Order;
import com.shopnow.domain.model.Product;
import com.shopnow.domain.model.ProductVariant;
import com.shopnow.domain.model.Promotion;
import com.shopnow.domain.port.CartRepository;
import com.shopnow.domain.port.OrderRepository;
import com.shopnow.domain.port.ProductVariantRepository;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private CartRepository cartRepository;
    @Mock private ProductVariantRepository productVariantRepository;
    @Mock private InventoryService inventoryService;
    @Mock private PromotionService promotionService;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, cartRepository, productVariantRepository,
                inventoryService, promotionService);
    }

    private ProductVariant variant(Long id) {
        Category category = new Category("Electronics", "electronics");
        Product product = new Product("iPhone 15", "iphone-15", category, new BigDecimal("999.00"));
        ProductVariant v = new ProductVariant(product, "SKU-1", new BigDecimal("999.00"));
        try {
            var f = ProductVariant.class.getDeclaredField("id"); f.setAccessible(true); f.set(v, id);
        } catch (Exception e) { throw new RuntimeException(e); }
        return v;
    }

    @Test
    void shouldPlaceOrderWithoutCoupon() {
        Cart cart = new Cart(1L);
        cart.addItem(7L, "SKU-1", new BigDecimal("999.00"), 1);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(promotionService.validateAndApply(null, 1L, new BigDecimal("999.00")))
                .thenReturn(new PromotionService.DiscountResult(BigDecimal.ZERO, null));
        when(productVariantRepository.findById(7L)).thenReturn(Optional.of(variant(7L)));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            var f = Order.class.getDeclaredField("id"); f.setAccessible(true); f.set(o, 1L);
            return o;
        });

        OrderDto result = orderService.placeOrder(1L, null);

        assertEquals(new BigDecimal("999.00"), result.totalAmount());
        assertEquals(0, result.discountAmount().compareTo(BigDecimal.ZERO));
        verify(inventoryService).commitStock(anyList());
        verify(promotionService, never()).recordRedemption(any(), any(), any());
        verify(cartRepository).deleteByUserId(1L);
    }

    @Test
    void shouldApplyCouponDiscount() {
        Cart cart = new Cart(1L);
        cart.addItem(7L, "SKU-1", new BigDecimal("100.00"), 1);
        Promotion promo = new Promotion("TEN", Promotion.PromoType.FIXED, new BigDecimal("10"),
                null, null, java.time.LocalDateTime.now().minusDays(1),
                java.time.LocalDateTime.now().plusDays(1), Promotion.PromoStatus.ACTIVE);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(promotionService.validateAndApply("TEN", 1L, new BigDecimal("100.00")))
                .thenReturn(new PromotionService.DiscountResult(new BigDecimal("10"), promo));
        when(productVariantRepository.findById(7L)).thenReturn(Optional.of(variant(7L)));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            var f = Order.class.getDeclaredField("id"); f.setAccessible(true); f.set(o, 1L);
            return o;
        });

        OrderDto result = orderService.placeOrder(1L, "TEN");

        assertEquals(new BigDecimal("90.00"), result.totalAmount());
        assertEquals(new BigDecimal("10"), result.discountAmount());
        verify(promotionService).recordRedemption(promo, 1L, 1L);
    }

    @Test
    void shouldThrowWhenCartEmpty() {
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> orderService.placeOrder(1L, null));
    }

    @Test
    void shouldThrowWhenVariantMissing() {
        Cart cart = new Cart(1L);
        cart.addItem(99L, "SKU-9", new BigDecimal("100.00"), 1);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(promotionService.validateAndApply(null, 1L, new BigDecimal("100.00")))
                .thenReturn(new PromotionService.DiscountResult(BigDecimal.ZERO, null));
        when(productVariantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> orderService.placeOrder(1L, null));
        verify(inventoryService, never()).commitStock(anyList());
    }

    @Test
    void shouldCancelOrderAndRestoreStock() {
        Order order = new Order(1L, java.util.List.of(), BigDecimal.ZERO);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderDto result = orderService.cancelOrder(1L);

        assertEquals("CANCELLED", result.status());
        verify(inventoryService).restoreStock(anyList());
        verify(promotionService).reverseRedemption(1L);
    }

    @Test
    void shouldGetOrder() {
        Order order = new Order(1L, java.util.List.of(), new BigDecimal("50.00"));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        OrderDto result = orderService.getOrder(1L);
        assertEquals(new BigDecimal("50.00"), result.totalAmount());
    }
}
