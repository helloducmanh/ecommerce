package com.shopnow.domain.model;

import jakarta.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "product_variant_values")
public class ProductVariantValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attribute_value_id", nullable = false)
    private AttributeValue attributeValue;

    protected ProductVariantValue() {}

    public ProductVariantValue(ProductVariant variant, AttributeValue attributeValue) {
        this.variant = variant;
        this.attributeValue = attributeValue;
    }

    public Long getId() { return id; }
    public ProductVariant getVariant() { return variant; }
    public AttributeValue getAttributeValue() { return attributeValue; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProductVariantValue that = (ProductVariantValue) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
