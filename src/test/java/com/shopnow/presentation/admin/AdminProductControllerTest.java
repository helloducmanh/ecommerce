package com.shopnow.presentation.admin;

import com.shopnow.application.catalog.ProductService;
import com.shopnow.infrastructure.config.SecurityConfig;
import com.shopnow.infrastructure.security.TestSecurityConfig;
import com.shopnow.presentation.dto.ProductDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminProductController.class)
@Import({SecurityConfig.class, TestSecurityConfig.class})
class AdminProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldCreateProduct() throws Exception {
        when(productService.createProduct(any()))
            .thenReturn(new ProductDto(1L, "iPhone 15", "iphone-15", "Desc",
                new BigDecimal("999.00"), "Electronics", List.of()));

        mockMvc.perform(post("/api/v1/admin/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "name": "iPhone 15",
                        "slug": "iphone-15",
                        "description": "Desc",
                        "categoryId": 1,
                        "basePrice": 999.00
                    }
                    """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("iPhone 15"));
    }

    @Test
    void shouldRejectCreateProductWithoutAdminRole() throws Exception {
        mockMvc.perform(post("/api/v1/admin/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "name": "iPhone 15",
                        "slug": "iphone-15",
                        "description": "Desc",
                        "categoryId": 1,
                        "basePrice": 999.00
                    }
                    """))
                .andExpect(status().isUnauthorized());
    }
}
