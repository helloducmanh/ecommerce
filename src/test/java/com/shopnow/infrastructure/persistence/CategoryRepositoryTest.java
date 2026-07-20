package com.shopnow.infrastructure.persistence;

import com.shopnow.domain.model.Category;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CategoryRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private CategoryJpaRepository categoryJpaRepository;

    @Test
    void shouldSaveCategory() {
        Category category = new Category("Electronics", "electronics");

        Category saved = categoryJpaRepository.save(category);

        assertNotNull(saved.getId());
        assertEquals("Electronics", saved.getName());
        assertEquals("electronics", saved.getSlug());
    }

    @Test
    void shouldFindBySlug() {
        Category category = new Category("Electronics", "electronics");
        categoryJpaRepository.save(category);

        Optional<Category> found = categoryJpaRepository.findBySlug("electronics");

        assertTrue(found.isPresent());
        assertEquals("Electronics", found.get().getName());
    }

    @Test
    void shouldFindRootCategories() {
        Category parent = new Category("Electronics", "electronics");
        Category child = new Category("Laptops", "laptops");
        child.setParent(parent);

        categoryJpaRepository.save(parent);
        categoryJpaRepository.save(child);

        List<Category> roots = categoryJpaRepository.findRootCategories();

        assertEquals(1, roots.size());
        assertEquals("Electronics", roots.get(0).getName());
    }

    @Test
    void shouldDeleteCategory() {
        Category category = new Category("Electronics", "electronics");
        Category saved = categoryJpaRepository.save(category);

        categoryJpaRepository.deleteById(saved.getId());

        Optional<Category> found = categoryJpaRepository.findById(saved.getId());
        assertFalse(found.isPresent());
    }
}
