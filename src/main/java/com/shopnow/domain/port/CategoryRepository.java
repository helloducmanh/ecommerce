package com.shopnow.domain.port;

import com.shopnow.domain.model.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository {

    Category save(Category category);

    Optional<Category> findById(Long id);

    Optional<Category> findBySlug(String slug);

    List<Category> findAll();

    List<Category> findRootCategories();

    void deleteById(Long id);
}
