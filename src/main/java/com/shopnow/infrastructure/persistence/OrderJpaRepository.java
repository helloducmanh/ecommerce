package com.shopnow.infrastructure.persistence;

import com.shopnow.domain.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserId(Long userId);

    @Query("""
            SELECT CASE WHEN COUNT(o.id) > 0 THEN TRUE ELSE FALSE END
            FROM Order o JOIN o.items i
            WHERE o.userId = :userId
              AND i.productId = :productId
              AND o.status IN :statuses
            """)
    boolean existsPurchaseOfProduct(
            @Param("userId") Long userId,
            @Param("productId") Long productId,
            @Param("statuses") List<String> statuses);
}
