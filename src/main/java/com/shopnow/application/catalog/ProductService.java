package com.shopnow.application.catalog;

import com.shopnow.domain.model.Category;
import com.shopnow.domain.model.Product;
import com.shopnow.domain.port.CategoryRepository;
import com.shopnow.domain.port.ProductRepository;
import com.shopnow.presentation.dto.CreateProductRequest;
import com.shopnow.presentation.dto.ProductDto;
import com.shopnow.presentation.dto.ProductVariantDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public ProductDto createProduct(CreateProductRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
            .orElseThrow(() -> new IllegalArgumentException("Category not found"));

        Product product = new Product(request.name(), request.slug(), category, request.basePrice());
        product.setDescription(request.description());

        Product saved = productRepository.save(product);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public ProductDto getProductBySlug(String slug) {
        Product product = productRepository.findBySlug(slug)
            .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        return toDto(product);
    }

    @Transactional(readOnly = true)
    public List<ProductDto> getProductsByCategory(Long categoryId) {
        return productRepository.findByCategoryId(categoryId).stream()
            .map(this::toDto)
            .toList();
    }

    private ProductDto toDto(Product product) {
        List<ProductVariantDto> variants = product.getVariants().stream()
            .map(v -> new ProductVariantDto(
                v.getId(),
                v.getSku(),
                v.getPrice(),
                v.getVariantName()
            ))
            .toList();

        return new ProductDto(
            product.getId(),
            product.getName(),
            product.getSlug(),
            product.getDescription(),
            product.getBasePrice(),
            product.getCategory().getName(),
            variants
        );
    }
}
