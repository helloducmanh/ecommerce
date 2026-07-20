// src/test/java/com/shopnow/presentation/api/CartControllerTest.java
package com.shopnow.presentation.api;

import com.shopnow.application.cart.CartService;
import com.shopnow.infrastructure.config.SecurityConfig;
import com.shopnow.infrastructure.security.TestSecurityConfig;
import com.shopnow.infrastructure.security.UserPrincipal;
import com.shopnow.presentation.dto.CartDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CartController.class)
@Import({SecurityConfig.class, TestSecurityConfig.class})
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CartService cartService;

    private org.springframework.security.core.Authentication principal() {
        UserPrincipal user = new UserPrincipal(1L, "alice@example.com", "CUSTOMER");
        return new UsernamePasswordAuthenticationToken(user, null, user.authorities());
    }

    @Test
    void shouldGetCartForAuthenticatedUser() throws Exception {
        when(cartService.getCart(1L)).thenReturn(new CartDto(1L, List.of(), BigDecimal.ZERO));

        mockMvc.perform(get("/api/v1/cart").with(authentication(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1));
    }

    @Test
    void shouldRejectCartWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isUnauthorized());
    }
}
