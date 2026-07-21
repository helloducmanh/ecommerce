// src/test/java/com/shopnow/presentation/security/ReviewSecurityTest.java
package com.shopnow.presentation.security;

import com.shopnow.domain.model.Product;
import com.shopnow.domain.model.Review;
import com.shopnow.domain.port.ReviewRepository;
import com.shopnow.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewSecurityTest {

    @Mock
    private ReviewRepository reviewRepository;

    private ReviewSecurity reviewSecurity;

    @BeforeEach
    void setUp() {
        reviewSecurity = new ReviewSecurity(reviewRepository);
    }

    private Authentication authFor(Long userId) {
        UserPrincipal principal = new UserPrincipal(userId, "u@example.com", "CUSTOMER");
        return new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
    }

    @Test
    void shouldAllowOwner() {
        Product product = new Product("x", "x", null, BigDecimal.ZERO);
        Review review = new Review(product, 1L, "Alice", 5, "c");
        when(reviewRepository.findById(10L)).thenReturn(Optional.of(review));

        assertTrue(reviewSecurity.isOwner(10L, authFor(1L)));
    }

    @Test
    void shouldRejectNonOwner() {
        Product product = new Product("x", "x", null, BigDecimal.ZERO);
        Review review = new Review(product, 1L, "Alice", 5, "c");
        when(reviewRepository.findById(10L)).thenReturn(Optional.of(review));

        assertFalse(reviewSecurity.isOwner(10L, authFor(999L)));
    }

    @Test
    void shouldRejectWhenReviewMissing() {
        when(reviewRepository.findById(10L)).thenReturn(Optional.empty());

        assertFalse(reviewSecurity.isOwner(10L, authFor(1L)));
    }

    @Test
    void shouldRejectAnonymousPrincipal() {
        Authentication anonymous = new UsernamePasswordAuthenticationToken("anon", null, java.util.List.of());
        assertFalse(reviewSecurity.isOwner(10L, anonymous));
    }
}
