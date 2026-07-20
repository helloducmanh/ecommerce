package com.shopnow.presentation.admin;

import com.shopnow.application.catalog.ProductService;
import com.shopnow.presentation.dto.CreateProductRequest;
import com.shopnow.presentation.dto.ProductDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/products")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductController {

    private final ProductService productService;

    public AdminProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<ProductDto> createProduct(@Valid @RequestBody CreateProductRequest request) {
        ProductDto product = productService.createProduct(request);
        return ResponseEntity.status(201).body(product);
    }
}
