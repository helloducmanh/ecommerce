package com.shopnow.presentation.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopnow.application.cart.CartService;
import com.shopnow.infrastructure.config.SecurityConfig;
import com.shopnow.presentation.dto.CartDto;
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

@WebMvcTest(CartController.class)
@Import(SecurityConfig.class)
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CartService cartService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldGetCart() throws Exception {
        when(cartService.getCart(1L))
            .thenReturn(new CartDto(1L, List.of(), BigDecimal.ZERO));

        mockMvc.perform(get("/api/v1/cart").param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1));
    }
}
