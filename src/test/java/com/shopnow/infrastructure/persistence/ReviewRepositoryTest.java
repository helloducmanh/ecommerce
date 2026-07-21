// src/test/java/com/shopnow/infrastructure/persistence/ReviewRepositoryTest.java
package com.shopnow.infrastructure.persistence;

import com.shopnow.domain.model.Category;
import com.shopnow.domain.model.Product;
import com.shopnow.domain.model.Review;
import com.shopnow.domain.port.ReviewRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ReviewRepositoryImpl.class)
class ReviewRepositoryTest {

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
    private TestEntityManager em;

    @Autowired
    private ReviewJpaRepository reviewJpaRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    private Product persistProduct() {
        Category category = em.persistFlushFind(new Category("Electronics", "electronics"));
        return em.persistFlushFind(new Product("iPhone 15", "iphone-15", category, new BigDecimal("999.00")));
    }

    @Test
    void shouldSaveAndFindById() {
        Product product = persistProduct();
        Review saved = reviewRepository.save(new Review(product, 1L, "Alice", 5, "Great"));
        em.flush();
        em.clear();

        assertTrue(reviewRepository.findById(saved.getId()).isPresent());
    }

    @Test
    void shouldFindByProductIdPaginated() {
        Product product = persistProduct();
        reviewRepository.save(new Review(product, 1L, "Alice", 5, "a"));
        reviewRepository.save(new Review(product, 2L, "Bob", 4, "b"));
        em.flush();
        em.clear();

        var page = reviewRepository.findByProductId(product.getId(), PageRequest.of(0, 10));
        assertEquals(2, page.getTotalElements());
    }

    @Test
    void shouldReportExistenceByUserAndProduct() {
        Product product = persistProduct();
        reviewRepository.save(new Review(product, 1L, "Alice", 5, "Great"));
        em.flush();
        em.clear();

        assertTrue(reviewRepository.existsByUserIdAndProductId(1L, product.getId()));
        assertFalse(reviewRepository.existsByUserIdAndProductId(2L, product.getId()));
    }

    @Test
    void shouldAggregateRating() {
        Product product = persistProduct();
        reviewRepository.save(new Review(product, 1L, "Alice", 5, "a"));
        reviewRepository.save(new Review(product, 2L, "Bob", 4, "b"));
        reviewRepository.save(new Review(product, 3L, "Cara", 3, "c"));
        em.flush();
        em.clear();

        ReviewRepository.RatingStats stats = reviewRepository.countAndAvgRatingByProductId(product.getId());
        assertEquals(3, stats.count());
        assertEquals(4.0, stats.average(), 0.001);
    }

    @Test
    void shouldAggregateZeroForProductWithoutReviews() {
        Product product = persistProduct();
        em.flush();
        em.clear();

        ReviewRepository.RatingStats stats = reviewRepository.countAndAvgRatingByProductId(product.getId());
        assertEquals(0, stats.count());
        assertEquals(0.0, stats.average(), 0.001);
    }
}
