package com.shopnow.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, unique = true, length = 255)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;

    @Column(precision = 2, scale = 1)
    private BigDecimal avgRating = BigDecimal.ZERO;

    @Column(nullable = false)
    private Integer reviewCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status = ProductStatus.ACTIVE;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductVariant> variants = new ArrayList<>();

    protected Product() {}

    public Product(String name, String slug, Category category, BigDecimal basePrice) {
        this.name = name;
        this.slug = slug;
        this.category = category;
        this.basePrice = basePrice;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public BigDecimal getBasePrice() { return basePrice; }
    public BigDecimal getAvgRating() { return avgRating; }
    public Integer getReviewCount() { return reviewCount; }
    public ProductStatus getStatus() { return status; }
    public List<ProductVariant> getVariants() { return variants; }

    public void setDescription(String description) { this.description = description; }

    public void setReviewCount(Integer reviewCount) { this.reviewCount = reviewCount; }

    public void setAvgRating(java.math.BigDecimal avgRating) { this.avgRating = avgRating; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Product product = (Product) o;
        return Objects.equals(id, product.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public enum ProductStatus {
        ACTIVE, INACTIVE, DELETED
    }
}
