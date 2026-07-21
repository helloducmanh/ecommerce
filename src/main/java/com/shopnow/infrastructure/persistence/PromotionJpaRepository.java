// src/main/java/com/shopnow/infrastructure/persistence/PromotionJpaRepository.java
package com.shopnow.infrastructure.persistence;

import com.shopnow.domain.model.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PromotionJpaRepository extends JpaRepository<Promotion, Long> {
    Optional<Promotion> findByCode(String code);
}
