// src/main/java/com/shopnow/presentation/api/CartController.java
package com.shopnow.presentation.api;

import com.shopnow.application.cart.CartService;
import com.shopnow.infrastructure.security.UserPrincipal;
import com.shopnow.presentation.dto.AddCartItemRequest;
import com.shopnow.presentation.dto.CartDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ResponseEntity<CartDto> getCart(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(cartService.getCart(principal.userId()));
    }

    @PostMapping("/items")
    public ResponseEntity<CartDto> addItem(@AuthenticationPrincipal UserPrincipal principal,
                                           @Valid @RequestBody AddCartItemRequest request) {
        return ResponseEntity.status(201).body(cartService.addItem(principal.userId(), request));
    }

    @PatchMapping("/items/{variantId}")
    public ResponseEntity<CartDto> updateItemQuantity(@AuthenticationPrincipal UserPrincipal principal,
                                                      @PathVariable Long variantId,
                                                      @RequestParam Integer quantity) {
        return ResponseEntity.ok(cartService.updateItemQuantity(principal.userId(), variantId, quantity));
    }

    @DeleteMapping("/items/{variantId}")
    public ResponseEntity<Void> removeItem(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable Long variantId) {
        cartService.removeItem(principal.userId(), variantId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(@AuthenticationPrincipal UserPrincipal principal) {
        cartService.clearCart(principal.userId());
        return ResponseEntity.noContent().build();
    }
}
