// src/main/java/com/shopnow/infrastructure/persistence/ReviewRepositoryImpl.java
package com.shopnow.infrastructure.persistence;

import com.shopnow.domain.model.Review;
import com.shopnow.domain.port.ReviewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ReviewRepositoryImpl implements ReviewRepository {

    private final ReviewJpaRepository jpaRepository;

    public ReviewRepositoryImpl(ReviewJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Review save(Review review) {
        return jpaRepository.save(review);
    }

    @Override
    public Optional<Review> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Page<Review> findByProductId(Long productId, Pageable pageable) {
        return jpaRepository.findByProductId(productId, pageable);
    }

    @Override
    public boolean existsByUserIdAndProductId(Long userId, Long productId) {
        return jpaRepository.existsByUserIdAndProductId(userId, productId);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public RatingStats countAndAvgRatingByProductId(Long productId) {
        List<Object[]> rows = jpaRepository.aggregateRating(productId);
        Object[] row = rows.isEmpty() ? new Object[]{0L, 0.0} : rows.get(0);
        long count = ((Number) row[0]).longValue();
        double average = ((Number) row[1]).doubleValue();
        return new RatingStats(count, average);
    }
}
