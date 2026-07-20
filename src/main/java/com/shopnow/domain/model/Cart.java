package com.shopnow.domain.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class Cart {

    private Long userId;
    private List<CartItem> items;

    public Cart(Long userId) {
        this.userId = userId;
        this.items = new ArrayList<>();
    }

    public Long getUserId() { return userId; }
    public List<CartItem> getItems() { return items; }

    public void addItem(Long variantId, String sku, BigDecimal price, Integer quantity) {
        CartItem existing = items.stream()
            .filter(item -> item.getVariantId().equals(variantId))
            .findFirst()
            .orElse(null);

        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + quantity);
        } else {
            items.add(new CartItem(variantId, sku, price, quantity));
        }
    }

    public void updateItemQuantity(Long variantId, Integer quantity) {
        CartItem item = items.stream()
            .filter(i -> i.getVariantId().equals(variantId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Item not found in cart"));
        item.setQuantity(quantity);
    }

    public void removeItem(Long variantId) {
        items.removeIf(item -> item.getVariantId().equals(variantId));
    }

    public void clear() {
        items.clear();
    }

    public BigDecimal getTotal() {
        return items.stream()
            .map(CartItem::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
