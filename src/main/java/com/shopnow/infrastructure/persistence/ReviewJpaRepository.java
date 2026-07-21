// src/main/java/com/shopnow/infrastructure/persistence/ReviewJpaRepository.java
package com.shopnow.infrastructure.persistence;

import com.shopnow.domain.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewJpaRepository extends JpaRepository<Review, Long> {

    Page<Review> findByProductId(Long productId, Pageable pageable);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    @Query("""
            SELECT COUNT(r.id), COALESCE(AVG(r.rating), 0.0)
            FROM Review r
            WHERE r.product.id = :productId
            """)
    List<Object[]> aggregateRating(@Param("productId") Long productId);
}
