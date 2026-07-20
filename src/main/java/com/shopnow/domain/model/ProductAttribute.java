package com.shopnow.domain.model;

import jakarta.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "product_attributes")
public class ProductAttribute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attribute_id", nullable = false)
    private Attribute attribute;

    @Column(nullable = false)
    private Boolean isVariantAxis = true;

    protected ProductAttribute() {}

    public ProductAttribute(Product product, Attribute attribute, Boolean isVariantAxis) {
        this.product = product;
        this.attribute = attribute;
        this.isVariantAxis = isVariantAxis;
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public Attribute getAttribute() { return attribute; }
    public Boolean getIsVariantAxis() { return isVariantAxis; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProductAttribute that = (ProductAttribute) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
