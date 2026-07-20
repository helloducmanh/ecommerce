package com.shopnow.domain.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CategoryTest {

    @Test
    void shouldCreateCategory() {
        Category category = new Category("Electronics", "electronics");
        assertEquals("Electronics", category.getName());
        assertEquals("electronics", category.getSlug());
        assertEquals(0, category.getDepth());
    }

    @Test
    void shouldSetParent() {
        Category parent = new Category("Electronics", "electronics");
        Category child = new Category("Laptops", "laptops");
        child.setParent(parent);

        assertEquals(parent, child.getParent());
        assertEquals(1, child.getDepth());
    }
}
