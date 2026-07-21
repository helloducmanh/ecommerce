package com.shopnow.presentation.admin;

import com.shopnow.application.inventory.InventoryService;
import com.shopnow.infrastructure.config.SecurityConfig;
import com.shopnow.infrastructure.security.TestSecurityConfig;
import com.shopnow.presentation.dto.LowStockDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminInventoryController.class)
@Import({SecurityConfig.class, TestSecurityConfig.class})
class AdminInventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InventoryService inventoryService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnLowStock() throws Exception {
        when(inventoryService.lowStock()).thenReturn(List.of(
                new LowStockDto(7L, "SKU-7", "iPhone 15", 2, 0, 2, 10)));

        mockMvc.perform(get("/api/v1/admin/inventory/low-stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].variantId").value(7))
                .andExpect(jsonPath("$[0].available").value(2))
                .andExpect(jsonPath("$[0].threshold").value(10));
    }

    @Test
    void shouldRejectWithoutAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/inventory/low-stock"))
                .andExpect(status().isUnauthorized());
    }
}
