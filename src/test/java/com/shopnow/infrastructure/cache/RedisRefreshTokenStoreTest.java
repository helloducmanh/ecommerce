// src/test/java/com/shopnow/infrastructure/cache/RedisRefreshTokenStoreTest.java
package com.shopnow.infrastructure.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisRefreshTokenStoreTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOps;

    private RedisRefreshTokenStore store;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        store = new RedisRefreshTokenStore(redisTemplate);
    }

    @Test
    void shouldStoreRefreshTokenWithTtl() {
        store.store("jti-1", 42L, Duration.ofDays(7));

        verify(valueOps).set(eq("refresh:jti-1"), eq(42L), eq(Duration.ofDays(7)));
    }

    @Test
    void shouldReportExistence() {
        when(redisTemplate.hasKey("refresh:jti-1")).thenReturn(true);
        when(redisTemplate.hasKey("refresh:jti-2")).thenReturn(false);

        assertTrue(store.exists("jti-1"));
        assertFalse(store.exists("jti-2"));
    }

    @Test
    void shouldRevokeToken() {
        store.revoke("jti-1");

        verify(redisTemplate).delete("refresh:jti-1");
    }
}
