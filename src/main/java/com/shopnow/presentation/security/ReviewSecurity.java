// src/main/java/com/shopnow/presentation/security/ReviewSecurity.java
package com.shopnow.presentation.security;

import com.shopnow.domain.model.Review;
import com.shopnow.domain.port.ReviewRepository;
import com.shopnow.infrastructure.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component("reviewSecurity")
public class ReviewSecurity {

    private final ReviewRepository reviewRepository;

    public ReviewSecurity(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    public boolean isOwner(Long reviewId, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return false;
        }
        Optional<Review> review = reviewRepository.findById(reviewId);
        return review.map(r -> r.getUserId().equals(principal.userId())).orElse(false);
    }
}
