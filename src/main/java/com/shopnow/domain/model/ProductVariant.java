package com.shopnow.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "product_variants")
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, unique = true, length = 64)
    private String sku;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Boolean priceOverride = false;

    @Column(length = 255)
    private String variantName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VariantStatus status = VariantStatus.ACTIVE;

    @OneToMany(mappedBy = "variant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductVariantValue> variantValues = new ArrayList<>();

    protected ProductVariant() {}

    public ProductVariant(Product product, String sku, BigDecimal price) {
        this.product = product;
        this.sku = sku;
        this.price = price;
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public String getSku() { return sku; }
    public BigDecimal getPrice() { return price; }
    public Boolean getPriceOverride() { return priceOverride; }
    public String getVariantName() { return variantName; }
    public VariantStatus getStatus() { return status; }
    public List<ProductVariantValue> getVariantValues() { return variantValues; }

    public void setVariantName(String variantName) { this.variantName = variantName; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProductVariant that = (ProductVariant) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public enum VariantStatus {
        ACTIVE, INACTIVE
    }
}
