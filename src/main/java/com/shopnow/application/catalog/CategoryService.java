package com.shopnow.application.catalog;

import com.shopnow.domain.model.Category;
import com.shopnow.domain.port.CategoryRepository;
import com.shopnow.presentation.dto.CategoryDto;
import com.shopnow.presentation.dto.CreateCategoryRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public CategoryDto createCategory(CreateCategoryRequest request) {
        Category category = new Category(request.name(), request.slug());

        if (request.parentId() != null) {
            Category parent = categoryRepository.findById(request.parentId())
                    .orElseThrow(() -> new IllegalArgumentException("Parent category not found"));
            category.setParent(parent);
        }

        Category saved = categoryRepository.save(category);
        return toDto(saved);
    }

    public List<CategoryDto> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    public List<CategoryDto> getRootCategories() {
        return categoryRepository.findRootCategories().stream()
                .map(this::toDto)
                .toList();
    }

    public CategoryDto getCategoryById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));
        return toDto(category);
    }

    @Transactional
    public void deleteCategory(Long id) {
        categoryRepository.deleteById(id);
    }

    private CategoryDto toDto(Category category) {
        Long parentId = (category.getParent() != null) ? category.getParent().getId() : null;
        return new CategoryDto(
                category.getId(),
                category.getName(),
                category.getSlug(),
                parentId,
                category.getDepth()
        );
    }
}
