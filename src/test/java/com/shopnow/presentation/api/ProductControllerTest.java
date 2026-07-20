package com.shopnow.presentation.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopnow.application.catalog.ProductService;
import com.shopnow.infrastructure.config.SecurityConfig;
import com.shopnow.presentation.dto.ProductDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldGetProductBySlug() throws Exception {
        when(productService.getProductBySlug("iphone-15"))
            .thenReturn(new ProductDto(1L, "iPhone 15", "iphone-15", "Description",
                new BigDecimal("999.00"), "Electronics", List.of()));

        mockMvc.perform(get("/api/v1/products/iphone-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("iPhone 15"))
                .andExpect(jsonPath("$.slug").value("iphone-15"));
    }
}
