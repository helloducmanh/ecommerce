package com.shopnow.application.cart;

import com.shopnow.domain.model.Cart;
import com.shopnow.domain.port.CartRepository;
import com.shopnow.presentation.dto.AddCartItemRequest;
import com.shopnow.presentation.dto.CartDto;
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
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartRepository);
    }

    @Test
    void shouldAddItemToCart() {
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        AddCartItemRequest request = new AddCartItemRequest(100L, 1);
        CartDto result = cartService.addItem(1L, request);

        assertEquals(1, result.items().size());
        verify(cartRepository).save(any(Cart.class));
    }

    @Test
    void shouldGetCart() {
        Cart cart = new Cart(1L);
        cart.addItem(100L, "SKU-001", new BigDecimal("999.00"), 1);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

        CartDto result = cartService.getCart(1L);

        assertEquals(1, result.items().size());
    }
}
