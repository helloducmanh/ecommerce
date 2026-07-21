// src/main/java/com/shopnow/presentation/admin/AdminPromotionController.java
package com.shopnow.presentation.admin;

import com.shopnow.application.promotion.PromotionService;
import com.shopnow.presentation.dto.CreatePromotionRequest;
import com.shopnow.presentation.dto.PromotionDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/promotions")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPromotionController {
    private final PromotionService promotionService;

    public AdminPromotionController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @PostMapping
    public ResponseEntity<PromotionDto> create(@Valid @RequestBody CreatePromotionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(promotionService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<PromotionDto>> list() {
        return ResponseEntity.ok(promotionService.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PromotionDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(promotionService.get(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PromotionDto> update(@PathVariable Long id,
                                                @Valid @RequestBody CreatePromotionRequest request) {
        return ResponseEntity.ok(promotionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        promotionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
