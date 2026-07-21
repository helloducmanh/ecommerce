// src/main/java/com/shopnow/domain/port/PromotionRepository.java
package com.shopnow.domain.port;

import com.shopnow.domain.model.Promotion;

import java.util.List;
import java.util.Optional;

public interface PromotionRepository {
    Promotion save(Promotion promotion);
    Optional<Promotion> findById(Long id);
    Optional<Promotion> findByCode(String code);
    List<Promotion> findAll();
    void deleteById(Long id);
    boolean existsById(Long id);
}
