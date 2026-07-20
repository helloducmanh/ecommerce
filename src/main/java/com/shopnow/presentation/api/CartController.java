package com.shopnow.presentation.api;

import com.shopnow.application.cart.CartService;
import com.shopnow.presentation.dto.AddCartItemRequest;
import com.shopnow.presentation.dto.CartDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ResponseEntity<CartDto> getCart(@RequestParam Long userId) {
        CartDto cart = cartService.getCart(userId);
        return ResponseEntity.ok(cart);
    }

    @PostMapping("/items")
    public ResponseEntity<CartDto> addItem(
        @RequestParam Long userId,
        @Valid @RequestBody AddCartItemRequest request
    ) {
        CartDto cart = cartService.addItem(userId, request);
        return ResponseEntity.status(201).body(cart);
    }

    @PatchMapping("/items/{variantId}")
    public ResponseEntity<CartDto> updateItemQuantity(
        @RequestParam Long userId,
        @PathVariable Long variantId,
        @RequestParam Integer quantity
    ) {
        CartDto cart = cartService.updateItemQuantity(userId, variantId, quantity);
        return ResponseEntity.ok(cart);
    }

    @DeleteMapping("/items/{variantId}")
    public ResponseEntity<Void> removeItem(
        @RequestParam Long userId,
        @PathVariable Long variantId
    ) {
        cartService.removeItem(userId, variantId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(@RequestParam Long userId) {
        cartService.clearCart(userId);
        return ResponseEntity.noContent().build();
    }
}
