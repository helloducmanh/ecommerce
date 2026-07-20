package com.shopnow.presentation.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopnow.application.catalog.CategoryService;
import com.shopnow.infrastructure.config.SecurityConfig;
import com.shopnow.infrastructure.security.TestSecurityConfig;
import com.shopnow.presentation.dto.CategoryDto;
import com.shopnow.presentation.dto.CreateCategoryRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CategoryController.class)
@Import({SecurityConfig.class, TestSecurityConfig.class})
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CategoryService categoryService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldCreateCategory() throws Exception {
        CreateCategoryRequest request = new CreateCategoryRequest("Electronics", "electronics", null);
        CategoryDto response = new CategoryDto(1L, "Electronics", "electronics", null, 0);

        when(categoryService.createCategory(any(CreateCategoryRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Electronics"))
                .andExpect(jsonPath("$.slug").value("electronics"));
    }

    @Test
    void shouldGetAllCategories() throws Exception {
        List<CategoryDto> categories = List.of(
                new CategoryDto(1L, "Electronics", "electronics", null, 0),
                new CategoryDto(2L, "Books", "books", null, 0)
        );

        when(categoryService.getAllCategories()).thenReturn(categories);

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Electronics"))
                .andExpect(jsonPath("$[1].name").value("Books"));
    }

    @Test
    void shouldRejectCreateCategoryWithoutAdminRole() throws Exception {
        CreateCategoryRequest request = new CreateCategoryRequest("Electronics", "electronics", null);

        // Anonymous -> 401 (HttpStatusEntryPoint UNAUTHORIZED).
        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void shouldRejectCreateCategoryForCustomer() throws Exception {
        CreateCategoryRequest request = new CreateCategoryRequest("Electronics", "electronics", null);

        // Authenticated CUSTOMER -> 403 (method security @PreAuthorize hasRole('ADMIN')).
        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void shouldRejectDeleteCategoryForCustomer() throws Exception {
        mockMvc.perform(delete("/api/v1/categories/1"))
                .andExpect(status().isForbidden());
    }
}
