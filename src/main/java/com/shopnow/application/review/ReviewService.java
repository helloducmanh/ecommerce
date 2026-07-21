package com.shopnow.application.review;

import com.shopnow.domain.model.Product;
import com.shopnow.domain.model.Review;
import com.shopnow.domain.model.ReviewExistsException;
import com.shopnow.domain.model.VerifiedPurchaseRequiredException;
import com.shopnow.domain.port.OrderQueryPort;
import com.shopnow.domain.port.ProductRepository;
import com.shopnow.domain.port.ReviewRepository;
import com.shopnow.presentation.dto.ReviewDto;
import com.shopnow.presentation.dto.ReviewPageDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderQueryPort orderQueryPort;

    public ReviewService(ReviewRepository reviewRepository,
                         ProductRepository productRepository,
                         OrderQueryPort orderQueryPort) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.orderQueryPort = orderQueryPort;
    }

    @Transactional
    public ReviewDto createReview(Long userId, String userName, String productSlug,
                                  Integer rating, String comment) {
        Product product = productRepository.findBySlug(productSlug)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        if (reviewRepository.existsByUserIdAndProductId(userId, product.getId())) {
            throw new ReviewExistsException();
        }
        if (!orderQueryPort.hasUserPurchasedProduct(userId, product.getId())) {
            throw new VerifiedPurchaseRequiredException();
        }

        Review saved = reviewRepository.save(
                new Review(product, userId, userName, rating, comment));
        recomputeAggregate(product);
        return toDto(saved);
    }

    @Transactional
    public void deleteReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));
        Product product = review.getProduct();
        reviewRepository.deleteById(reviewId);
        recomputeAggregate(product);
    }

    @Transactional(readOnly = true)
    public ReviewPageDto listReviews(String productSlug, int page, int size) {
        Product product = productRepository.findBySlug(productSlug)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        Pageable pageable = PageRequest.of(page, size);
        Page<Review> reviews = reviewRepository.findByProductId(product.getId(), pageable);
        ReviewRepository.RatingStats stats = reviewRepository.countAndAvgRatingByProductId(product.getId());

        List<ReviewDto> dtos = reviews.getContent().stream().map(this::toDto).toList();
        return new ReviewPageDto(
                product.getId(),
                rounded(stats.average()),
                (int) stats.count(),
                dtos,
                reviews.getTotalElements(),
                reviews.getTotalPages(),
                page
        );
    }

    private void recomputeAggregate(Product product) {
        ReviewRepository.RatingStats stats = reviewRepository.countAndAvgRatingByProductId(product.getId());
        product.setAvgRating(rounded(stats.average()));
        product.setReviewCount((int) stats.count());
        productRepository.save(product);
    }

    private BigDecimal rounded(double average) {
        return BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP);
    }

    private ReviewDto toDto(Review review) {
        return new ReviewDto(
                review.getId(),
                review.getProduct().getId(),
                review.getUserId(),
                review.getUserName(),
                review.getRating(),
                review.getComment(),
                review.getVerifiedPurchase(),
                review.getCreatedAt()
        );
    }
}
