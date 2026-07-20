package com.shopnow.domain.model;

import java.math.BigDecimal;

public class CartItem {

    private Long variantId;
    private String sku;
    private BigDecimal price;
    private Integer quantity;

    public CartItem(Long variantId, String sku, BigDecimal price, Integer quantity) {
        this.variantId = variantId;
        this.sku = sku;
        this.price = price;
        this.quantity = quantity;
    }

    public Long getVariantId() { return variantId; }
    public String getSku() { return sku; }
    public BigDecimal getPrice() { return price; }
    public Integer getQuantity() { return quantity; }

    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getSubtotal() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }
}
