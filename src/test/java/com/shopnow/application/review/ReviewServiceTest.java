package com.shopnow.application.review;

import com.shopnow.domain.model.Category;
import com.shopnow.domain.model.Product;
import com.shopnow.domain.model.Review;
import com.shopnow.domain.model.ReviewExistsException;
import com.shopnow.domain.model.VerifiedPurchaseRequiredException;
import com.shopnow.domain.port.OrderQueryPort;
import com.shopnow.domain.port.ProductRepository;
import com.shopnow.domain.port.ReviewRepository;
import com.shopnow.presentation.dto.ReviewDto;
import com.shopnow.presentation.dto.ReviewPageDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderQueryPort orderQueryPort;

    private ReviewService reviewService;

    private Product product() {
        Category category = new Category("Electronics", "electronics");
        return new Product("iPhone 15", "iphone-15", category, new BigDecimal("999.00"));
    }

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(reviewRepository, productRepository, orderQueryPort);
    }

    @Test
    void shouldCreateReviewForVerifiedPurchaser() {
        Product product = product();
        when(productRepository.findBySlug("iphone-15")).thenReturn(Optional.of(product));
        when(reviewRepository.existsByUserIdAndProductId(1L, null)).thenReturn(false);
        when(orderQueryPort.hasUserPurchasedProduct(1L, null)).thenReturn(true);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            try {
                var f = Review.class.getDeclaredField("id"); f.setAccessible(true); f.set(r, 100L);
            } catch (Exception ignored) {}
            return r;
        });
        when(reviewRepository.countAndAvgRatingByProductId(any())).thenReturn(
                new ReviewRepository.RatingStats(1, 5.0));

        ReviewDto result = reviewService.createReview(1L, "Alice Smith", "iphone-15", 5, "Great");

        assertEquals(5, result.rating());
        assertEquals("Alice Smith", result.userName());
        verify(reviewRepository).save(any(Review.class));
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void shouldRejectDuplicateReview() {
        Product product = product();
        when(productRepository.findBySlug("iphone-15")).thenReturn(Optional.of(product));
        when(reviewRepository.existsByUserIdAndProductId(eq(1L), any())).thenReturn(true);

        assertThrows(ReviewExistsException.class,
                () -> reviewService.createReview(1L, "Alice", "iphone-15", 5, "x"));
    }

    @Test
    void shouldRejectNonPurchaser() {
        Product product = product();
        when(productRepository.findBySlug("iphone-15")).thenReturn(Optional.of(product));
        when(reviewRepository.existsByUserIdAndProductId(eq(1L), any())).thenReturn(false);
        when(orderQueryPort.hasUserPurchasedProduct(eq(1L), any())).thenReturn(false);

        assertThrows(VerifiedPurchaseRequiredException.class,
                () -> reviewService.createReview(1L, "Alice", "iphone-15", 5, "x"));
    }

    @Test
    void shouldThrowWhenProductNotFoundOnCreate() {
        when(productRepository.findBySlug("missing")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> reviewService.createReview(1L, "Alice", "missing", 5, "x"));
    }

    @Test
    void shouldDeleteReviewAndRecompute() {
        Product product = product();
        Review review = new Review(product, 1L, "Alice", 5, "Great");
        try {
            var f = Review.class.getDeclaredField("id"); f.setAccessible(true); f.set(review, 50L);
        } catch (Exception ignored) {}
        when(reviewRepository.findById(50L)).thenReturn(Optional.of(review));
        when(reviewRepository.countAndAvgRatingByProductId(any())).thenReturn(
                new ReviewRepository.RatingStats(0, 0.0));

        reviewService.deleteReview(50L);

        verify(reviewRepository).deleteById(50L);
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void shouldThrowWhenDeletingUnknownReview() {
        when(reviewRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> reviewService.deleteReview(99L));
    }

    @Test
    void shouldListReviewsPaginated() {
        Product product = product();
        try {
            var f = Product.class.getDeclaredField("id"); f.setAccessible(true); f.set(product, 10L);
        } catch (Exception ignored) {}
        when(productRepository.findBySlug("iphone-15")).thenReturn(Optional.of(product));
        Review r1 = new Review(product, 1L, "Alice", 5, "a");
        Review r2 = new Review(product, 2L, "Bob", 4, "b");
        when(reviewRepository.findByProductId(eq(10L), any())).thenReturn(
                new PageImpl<>(List.of(r1, r2), PageRequest.of(0, 10), 2));
        when(reviewRepository.countAndAvgRatingByProductId(10L)).thenReturn(
                new ReviewRepository.RatingStats(2, 4.5));

        ReviewPageDto page = reviewService.listReviews("iphone-15", 0, 10);

        assertEquals(10L, page.productId());
        assertEquals(2, page.reviewCount());
        assertEquals(2, page.reviews().size());
        assertEquals(0, page.page());
    }
}
