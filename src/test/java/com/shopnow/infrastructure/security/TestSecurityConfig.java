package com.shopnow.infrastructure.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class TestSecurityConfig {

    private static final String TEST_SECRET = "test-secret-at-least-32-bytes-long-padding-padding";

    @Bean
    public JwtProperties jwtProperties() {
        return new JwtProperties(TEST_SECRET, 900, 604800);
    }

    @Bean
    public JwtService jwtService(JwtProperties jwtProperties) {
        return new JwtService(jwtProperties);
    }
}
