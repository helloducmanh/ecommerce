package com.shopnow.application.cart;

import com.shopnow.domain.model.Cart;
import com.shopnow.domain.port.CartRepository;
import com.shopnow.presentation.dto.AddCartItemRequest;
import com.shopnow.presentation.dto.CartDto;
import com.shopnow.presentation.dto.CartItemDto;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class CartService {

    private final CartRepository cartRepository;

    public CartService(CartRepository cartRepository) {
        this.cartRepository = cartRepository;
    }

    public CartDto addItem(Long userId, AddCartItemRequest request) {
        Cart cart = cartRepository.findByUserId(userId)
            .orElse(new Cart(userId));

        // TODO: Fetch variant price from ProductRepository
        // For now, use hardcoded price
        cart.addItem(request.variantId(), "SKU-" + request.variantId(),
            new BigDecimal("999.00"), request.quantity());

        cartRepository.save(cart);
        return toDto(cart);
    }

    public CartDto getCart(Long userId) {
        Cart cart = cartRepository.findByUserId(userId)
            .orElse(new Cart(userId));
        return toDto(cart);
    }

    public CartDto updateItemQuantity(Long userId, Long variantId, Integer quantity) {
        Cart cart = cartRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("Cart not found"));
        cart.updateItemQuantity(variantId, quantity);
        cartRepository.save(cart);
        return toDto(cart);
    }

    public CartDto removeItem(Long userId, Long variantId) {
        Cart cart = cartRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("Cart not found"));
        cart.removeItem(variantId);
        cartRepository.save(cart);
        return toDto(cart);
    }

    public void clearCart(Long userId) {
        cartRepository.deleteByUserId(userId);
    }

    private CartDto toDto(Cart cart) {
        var items = cart.getItems().stream()
            .map(item -> new CartItemDto(
                item.getVariantId(),
                item.getSku(),
                item.getPrice(),
                item.getQuantity(),
                item.getSubtotal()
            ))
            .toList();

        return new CartDto(cart.getUserId(), items, cart.getTotal());
    }
}
