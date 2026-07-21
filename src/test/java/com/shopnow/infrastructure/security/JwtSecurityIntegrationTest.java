// src/test/java/com/shopnow/infrastructure/security/JwtSecurityIntegrationTest.java
package com.shopnow.infrastructure.security;

import com.shopnow.application.order.OrderService;
import com.shopnow.domain.model.User;
import com.shopnow.domain.model.UserRole;
import com.shopnow.infrastructure.config.SecurityConfig;
import com.shopnow.presentation.dto.OrderDto;
import com.shopnow.presentation.security.OrderSecurity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(com.shopnow.presentation.api.OrderController.class)
@Import({SecurityConfig.class, TestSecurityConfig.class})
class JwtSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private OrderService orderService;

    @MockBean(name = "orderSecurity")
    private OrderSecurity orderSecurity;

    private User userWithId(long id, UserRole role) {
        User u = new User("x@example.com", "hash", "First", "Last");
        u.setRole(role);
        try {
            var f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(u, id);
        } catch (Exception ignored) {
        }
        return u;
    }

    @Test
    void shouldAllowWithValidCustomerToken() throws Exception {
        String token = jwtService.generateTokens(userWithId(1L, UserRole.CUSTOMER)).accessToken();
        when(orderSecurity.isOwner(anyLong(), any())).thenReturn(true);
        when(orderService.getOrder(1L)).thenReturn(
                new OrderDto(1L, 1L, "PENDING", new BigDecimal("999.00"), new BigDecimal("0.00"), List.of()));

        mockMvc.perform(get("/api/v1/orders/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/orders/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectTamperedToken() throws Exception {
        String token = jwtService.generateTokens(userWithId(1L, UserRole.CUSTOMER)).accessToken();
        String tampered = token.substring(0, token.length() - 4) + "XXXX";

        mockMvc.perform(get("/api/v1/orders/1").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectCustomerRoleForAdminEndpoint() throws Exception {
        // AdminProductController exposes POST /api/v1/admin/products; a CUSTOMER token must be forbidden (403),
        // not 404/405 — the security rule runs before handler resolution.
        String customerToken = jwtService.generateTokens(userWithId(1L, UserRole.CUSTOMER)).accessToken();

        mockMvc.perform(post("/api/v1/admin/products")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "x", "slug": "x", "categoryId": 1, "basePrice": 1.00 }
                                """))
                .andExpect(status().isForbidden());
    }
}
