// src/main/java/com/shopnow/infrastructure/persistence/PromotionJpaRepository.java
package com.shopnow.infrastructure.persistence;

import com.shopnow.domain.model.Promotion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PromotionJpaRepository extends JpaRepository<Promotion, Long> {
    Optional<Promotion> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Promotion p WHERE p.id = :id")
    Optional<Promotion> findByIdForUpdate(@Param("id") Long id);
}
