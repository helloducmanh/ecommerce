// src/test/java/com/shopnow/presentation/admin/AdminPromotionControllerTest.java
package com.shopnow.presentation.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopnow.application.promotion.PromotionService;
import com.shopnow.domain.model.Promotion;
import com.shopnow.domain.model.PromotionException;
import com.shopnow.infrastructure.config.SecurityConfig;
import com.shopnow.infrastructure.security.TestSecurityConfig;
import com.shopnow.presentation.dto.CreatePromotionRequest;
import com.shopnow.presentation.dto.PromotionDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminPromotionController.class)
@Import({SecurityConfig.class, TestSecurityConfig.class})
class AdminPromotionControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private PromotionService promotionService;
    @Autowired
    private ObjectMapper objectMapper;

    private CreatePromotionRequest validRequest() {
        return new CreatePromotionRequest(
                "summer20", Promotion.PromoType.PERCENTAGE, new BigDecimal("20"),
                null, 100, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                Promotion.PromoStatus.ACTIVE);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldCreatePromotion() throws Exception {
        when(promotionService.create(any())).thenReturn(
                new PromotionDto(1L, "SUMMER20", "PERCENTAGE", new BigDecimal("20"),
                        null, 100, 0, null, null, "ACTIVE"));
        mockMvc.perform(post("/api/v1/admin/promotions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUMMER20"));
    }

    @Test
    void shouldRejectCreateWithoutAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/admin/promotions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldListPromotions() throws Exception {
        when(promotionService.list()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/admin/promotions"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldRejectInvalidPercentageValue() throws Exception {
        doThrow(new PromotionException(PromotionException.Code.INVALID_VALUE, "bad"))
                .when(promotionService).create(any());
        mockMvc.perform(post("/api/v1/admin/promotions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_VALUE"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn404OnMissingPromotion() throws Exception {
        when(promotionService.get(9L)).thenThrow(
                new PromotionException(PromotionException.Code.NOT_FOUND, "missing"));
        mockMvc.perform(get("/api/v1/admin/promotions/9"))
                .andExpect(status().isNotFound());
    }
}
