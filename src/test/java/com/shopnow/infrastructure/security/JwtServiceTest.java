// src/test/java/com/shopnow/infrastructure/security/JwtServiceTest.java
package com.shopnow.infrastructure.security;

import com.shopnow.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET = "test-secret-at-least-32-bytes-long-padding-padding";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new JwtProperties(SECRET, 900, 604800));
    }

    private User user() {
        return new User("alice@example.com", "hash", "Alice", "Smith");
    }

    @Test
    void shouldGenerateAndDecodeAccessToken() {
        JwtService.TokenPair tokens = jwtService.generateTokens(user());

        Jwt decoded = jwtService.decode(tokens.accessToken());

        assertEquals("access", decoded.getClaim("type"));
        assertEquals("alice@example.com", decoded.getClaim("email"));
        assertEquals("CUSTOMER", decoded.getClaim("role"));
    }

    @Test
    void shouldGenerateRefreshTokenWithJti() {
        JwtService.TokenPair tokens = jwtService.generateTokens(user());

        Jwt decoded = jwtService.decode(tokens.refreshToken());

        assertEquals("refresh", decoded.getClaim("type"));
        assertNotNull(decoded.getClaim("jti"));
        assertEquals(tokens.refreshJti(), decoded.getClaim("jti"));
    }

    @Test
    void shouldRejectTamperedToken() {
        JwtService.TokenPair tokens = jwtService.generateTokens(user());
        String tampered = tokens.accessToken().substring(0, tokens.accessToken().length() - 4) + "XXXX";

        assertThrows(JwtException.class, () -> jwtService.decode(tampered));
    }

    @Test
    void shouldRejectExpiredToken() throws InterruptedException {
        JwtService shortLived = new JwtService(new JwtProperties(SECRET, 1, 1));
        JwtService.TokenPair tokens = shortLived.generateTokens(user());

        Thread.sleep(2000);
        assertThrows(JwtException.class, () -> jwtService.decode(tokens.accessToken()));
    }
}
