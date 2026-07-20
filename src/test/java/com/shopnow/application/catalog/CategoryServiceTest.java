package com.shopnow.application.catalog;

import com.shopnow.domain.model.Category;
import com.shopnow.domain.port.CategoryRepository;
import com.shopnow.presentation.dto.CategoryDto;
import com.shopnow.presentation.dto.CreateCategoryRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository);
    }

    @Test
    void shouldCreateCategory() {
        CreateCategoryRequest request = new CreateCategoryRequest("Electronics", "electronics", null);
        Category category = new Category("Electronics", "electronics");

        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category saved = invocation.getArgument(0);
            return saved;
        });

        CategoryDto result = categoryService.createCategory(request);

        assertNotNull(result);
        assertEquals("Electronics", result.name());
        assertEquals("electronics", result.slug());
        verify(categoryRepository, times(1)).save(any(Category.class));
    }

    @Test
    void shouldCreateCategoryWithParent() {
        Category parent = new Category("Electronics", "electronics");
        CreateCategoryRequest request = new CreateCategoryRequest("Laptops", "laptops", 1L);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryDto result = categoryService.createCategory(request);

        assertNotNull(result);
        assertEquals("Laptops", result.name());
        assertEquals(1, result.depth());
        verify(categoryRepository, times(1)).findById(1L);
    }

    @Test
    void shouldGetAllCategories() {
        Category cat1 = new Category("Electronics", "electronics");
        Category cat2 = new Category("Books", "books");

        when(categoryRepository.findAll()).thenReturn(List.of(cat1, cat2));

        List<CategoryDto> result = categoryService.getAllCategories();

        assertEquals(2, result.size());
        verify(categoryRepository, times(1)).findAll();
    }

    @Test
    void shouldGetCategoryById() {
        Category category = new Category("Electronics", "electronics");

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        CategoryDto result = categoryService.getCategoryById(1L);

        assertNotNull(result);
        assertEquals("Electronics", result.name());
        verify(categoryRepository, times(1)).findById(1L);
    }

    @Test
    void shouldThrowExceptionWhenCategoryNotFound() {
        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            categoryService.getCategoryById(999L);
        });
    }

    @Test
    void shouldDeleteCategory() {
        categoryService.deleteCategory(1L);

        verify(categoryRepository, times(1)).deleteById(1L);
    }
}
