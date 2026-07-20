package com.shopnow.application.catalog;

import com.shopnow.domain.model.Category;
import com.shopnow.domain.model.Product;
import com.shopnow.domain.port.CategoryRepository;
import com.shopnow.domain.port.ProductRepository;
import com.shopnow.presentation.dto.CreateProductRequest;
import com.shopnow.presentation.dto.ProductDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, categoryRepository);
    }

    @Test
    void shouldCreateProduct() {
        Category category = new Category("Electronics", "electronics");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateProductRequest request = new CreateProductRequest(
            "iPhone 15", "iphone-15", "Description", 1L, new BigDecimal("999.00")
        );

        ProductDto result = productService.createProduct(request);

        assertEquals("iPhone 15", result.name());
        assertEquals("iphone-15", result.slug());
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void shouldGetProductBySlug() {
        Category category = new Category("Electronics", "electronics");
        Product product = new Product("iPhone 15", "iphone-15", category, new BigDecimal("999.00"));
        when(productRepository.findBySlug("iphone-15")).thenReturn(Optional.of(product));

        ProductDto result = productService.getProductBySlug("iphone-15");

        assertEquals("iPhone 15", result.name());
    }

    @Test
    void shouldThrowWhenCategoryNotFound() {
        CreateProductRequest request = new CreateProductRequest(
            "iPhone 15", "iphone-15", "Description", 999L, new BigDecimal("999.00")
        );
        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> productService.createProduct(request));
    }

    @Test
    void shouldGetProductsByCategory() {
        Category category = new Category("Electronics", "electronics");
        Product product = new Product("iPhone 15", "iphone-15", category, new BigDecimal("999.00"));
        when(productRepository.findByCategoryId(1L)).thenReturn(List.of(product));

        List<ProductDto> results = productService.getProductsByCategory(1L);

        assertEquals(1, results.size());
        assertEquals("iPhone 15", results.get(0).name());
    }
}
