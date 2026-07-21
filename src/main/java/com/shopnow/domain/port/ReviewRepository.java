// src/main/java/com/shopnow/domain/port/ReviewRepository.java
package com.shopnow.domain.port;

import com.shopnow.domain.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ReviewRepository {

    Review save(Review review);

    Optional<Review> findById(Long id);

    Page<Review> findByProductId(Long productId, Pageable pageable);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    void deleteById(Long id);

    RatingStats countAndAvgRatingByProductId(Long productId);

    record RatingStats(long count, double average) {
    }
}
