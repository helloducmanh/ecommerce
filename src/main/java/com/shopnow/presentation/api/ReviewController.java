// src/main/java/com/shopnow/presentation/api/ReviewController.java
package com.shopnow.presentation.api;

import com.shopnow.application.review.ReviewService;
import com.shopnow.domain.model.User;
import com.shopnow.domain.port.UserRepository;
import com.shopnow.infrastructure.security.UserPrincipal;
import com.shopnow.presentation.dto.CreateReviewRequest;
import com.shopnow.presentation.dto.ReviewDto;
import com.shopnow.presentation.dto.ReviewPageDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class ReviewController {

    private final ReviewService reviewService;
    private final UserRepository userRepository;

    public ReviewController(ReviewService reviewService, UserRepository userRepository) {
        this.reviewService = reviewService;
        this.userRepository = userRepository;
    }

    @GetMapping("/api/v1/products/{slug}/reviews")
    public ResponseEntity<ReviewPageDto> listReviews(@PathVariable String slug,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(reviewService.listReviews(slug, page, size));
    }

    @PostMapping("/api/v1/products/{slug}/reviews")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ReviewDto> createReview(@PathVariable String slug,
                                                  @AuthenticationPrincipal UserPrincipal principal,
                                                  @Valid @RequestBody CreateReviewRequest request) {
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        String userName = user.getFirstName() + " " + user.getLastName();
        ReviewDto created = reviewService.createReview(
                principal.userId(), userName, slug, request.rating(), request.comment());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/api/v1/reviews/{id}")
    @PreAuthorize("@reviewSecurity.isOwner(#id, authentication)")
    public ResponseEntity<Void> deleteReview(@PathVariable Long id) {
        reviewService.deleteReview(id);
        return ResponseEntity.noContent().build();
    }
}
