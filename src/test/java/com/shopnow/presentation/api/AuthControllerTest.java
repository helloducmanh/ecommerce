package com.shopnow.presentation.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopnow.application.auth.AuthService;
import com.shopnow.domain.model.EmailAlreadyExistsException;
import com.shopnow.domain.model.InvalidCredentialsException;
import com.shopnow.infrastructure.config.SecurityConfig;
import com.shopnow.presentation.dto.AuthResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldRegister() throws Exception {
        when(authService.register(any()))
                .thenReturn(new AuthResponse("access", "refresh", "Bearer", 900L));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "alice@example.com",
                                    "password": "password123",
                                    "firstName": "Alice",
                                    "lastName": "Smith"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void shouldReturn409OnDuplicateEmail() throws Exception {
        doThrow(new EmailAlreadyExistsException("alice@example.com"))
                .when(authService).register(any());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "alice@example.com",
                                    "password": "password123",
                                    "firstName": "Alice",
                                    "lastName": "Smith"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").exists());
    }

    @Test
    void shouldLogin() throws Exception {
        when(authService.login(any()))
                .thenReturn(new AuthResponse("access", "refresh", "Bearer", 900L));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "alice@example.com",
                                    "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"));
    }

    @Test
    void shouldReturn401OnBadCredentials() throws Exception {
        doThrow(new InvalidCredentialsException("Invalid email or password"))
                .when(authService).login(any());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "alice@example.com",
                                    "password": "wrong"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").exists());
    }

    @Test
    void shouldRejectRegisterWithInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "not-an-email", "password": "short" }
                                """))
                .andExpect(status().isBadRequest());
    }
}
