// src/test/java/com/shopnow/presentation/api/OrderControllerTest.java
package com.shopnow.presentation.api;

import com.shopnow.application.order.OrderService;
import com.shopnow.infrastructure.config.SecurityConfig;
import com.shopnow.infrastructure.security.TestSecurityConfig;
import com.shopnow.infrastructure.security.UserPrincipal;
import com.shopnow.presentation.dto.OrderDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, TestSecurityConfig.class})
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean(name = "orderSecurity")
    private com.shopnow.presentation.security.OrderSecurity orderSecurity;

    private Authentication principal() {
        UserPrincipal user = new UserPrincipal(1L, "alice@example.com", "CUSTOMER");
        return new UsernamePasswordAuthenticationToken(user, null, user.authorities());
    }

    @Test
    void shouldGetOrderWhenOwner() throws Exception {
        when(orderSecurity.isOwner(eq(1L), any(Authentication.class))).thenReturn(true);
        when(orderService.getOrder(1L))
                .thenReturn(new OrderDto(1L, 1L, "PENDING", new BigDecimal("999.00"), List.of()));

        mockMvc.perform(get("/api/v1/orders/1").with(authentication(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void shouldForbidOrderWhenNotOwner() throws Exception {
        when(orderSecurity.isOwner(eq(1L), any(Authentication.class))).thenReturn(false);

        mockMvc.perform(get("/api/v1/orders/1").with(authentication(principal())))
                .andExpect(status().isForbidden());
    }
}
