// src/main/java/com/shopnow/domain/model/Review.java
package com.shopnow.domain.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reviews",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "product_id"}))
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_name", nullable = false, length = 201)
    private String userName;

    @Column(nullable = false)
    private Integer rating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "verified_purchase", nullable = false)
    private Boolean verifiedPurchase = true;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected Review() {
    }

    public Review(Product product, Long userId, String userName, Integer rating, String comment) {
        this.product = product;
        this.userId = userId;
        this.userName = userName;
        this.rating = rating;
        this.comment = comment;
        this.verifiedPurchase = true;
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public Long getUserId() { return userId; }
    public String getUserName() { return userName; }
    public Integer getRating() { return rating; }
    public String getComment() { return comment; }
    public Boolean getVerifiedPurchase() { return verifiedPurchase; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
