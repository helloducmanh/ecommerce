package com.shopnow.presentation.admin;

import com.shopnow.application.catalog.CategoryService;
import com.shopnow.presentation.dto.CategoryDto;
import com.shopnow.presentation.dto.CreateCategoryRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/categories")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCategoryController {

    private final CategoryService categoryService;

    public AdminCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    public ResponseEntity<CategoryDto> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        CategoryDto category = categoryService.createCategory(request);
        return ResponseEntity.status(201).body(category);
    }
}
