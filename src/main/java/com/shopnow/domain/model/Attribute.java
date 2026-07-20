package com.shopnow.domain.model;

import jakarta.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "attributes")
public class Attribute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttributeType type = AttributeType.TEXT;

    protected Attribute() {}

    public Attribute(String name) {
        this.name = name;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public AttributeType getType() { return type; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Attribute attribute = (Attribute) o;
        return Objects.equals(id, attribute.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public enum AttributeType {
        TEXT, NUMBER, SWATCH
    }
}
