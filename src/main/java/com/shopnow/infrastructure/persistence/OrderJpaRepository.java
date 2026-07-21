package com.shopnow.infrastructure.persistence;

import com.shopnow.domain.model.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

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
