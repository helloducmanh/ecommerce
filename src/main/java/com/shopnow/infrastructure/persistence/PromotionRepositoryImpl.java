// src/main/java/com/shopnow/infrastructure/persistence/PromotionRepositoryImpl.java
package com.shopnow.infrastructure.persistence;

import com.shopnow.domain.model.Promotion;
import com.shopnow.domain.port.PromotionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PromotionRepositoryImpl implements PromotionRepository {
    private final PromotionJpaRepository jpaRepository;

    public PromotionRepositoryImpl(PromotionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Promotion save(Promotion promotion) {
        return jpaRepository.save(promotion);
    }

    @Override
    public Optional<Promotion> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<Promotion> findByIdForUpdate(Long id) {
        return jpaRepository.findByIdForUpdate(id);
    }

    @Override
    public Optional<Promotion> findByCode(String code) {
        return jpaRepository.findByCode(code == null ? null : code.toUpperCase());
    }

    @Override
    public List<Promotion> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public boolean existsById(Long id) {
        return jpaRepository.existsById(id);
    }
}
