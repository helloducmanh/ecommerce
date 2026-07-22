package com.shopnow.domain.model;

import jakarta.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false, unique = true)
    private ProductVariant variant;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private Integer reserved = 0;

    @Column(nullable = false)
    private Integer threshold = 10;

    protected Inventory() {}

    public Inventory(ProductVariant variant, Integer quantity) {
        this.variant = variant;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public ProductVariant getVariant() { return variant; }
    public Integer getQuantity() { return quantity; }
    public Integer getReserved() { return reserved; }
    public Integer getThreshold() { return threshold; }

    public Integer getAvailable() {
        return quantity - reserved;
    }

    public void reserve(Integer qty) {
        if (getAvailable() < qty) {
            throw new IllegalStateException("Insufficient stock");
        }
        this.reserved += qty;
    }

    public void commitReservation(Integer qty) {
        this.quantity -= qty;
        this.reserved -= qty;
    }

    public void releaseReservation(Integer qty) {
        this.reserved -= qty;
    }

    /**
     * Restore a quantity that was previously committed (e.g. when an order is cancelled).
     * Increases {@code quantity}; {@code reserved} is already 0 for a committed line, so it is untouched.
     */
    public void restoreCommitted(Integer qty) {
        this.quantity += qty;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Inventory inventory = (Inventory) o;
        return Objects.equals(id, inventory.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
