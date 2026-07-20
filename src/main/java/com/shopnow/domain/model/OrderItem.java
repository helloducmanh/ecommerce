package com.shopnow.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Long variantId;

    @Column(nullable = false, length = 255)
    private String productName;

    @Column(length = 255)
    private String variantName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    protected OrderItem() {}

    public OrderItem(Long productId, Long variantId, String productName,
                     String variantName, Integer quantity, BigDecimal unitPrice) {
        this.productId = productId;
        this.variantId = variantId;
        this.productName = productName;
        this.variantName = variantName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    void setOrder(Order order) { this.order = order; }

    public Long getId() { return id; }
    public Order getOrder() { return order; }
    public Long getProductId() { return productId; }
    public Long getVariantId() { return variantId; }
    public String getProductName() { return productName; }
    public String getVariantName() { return variantName; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }

    public BigDecimal getSubtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
