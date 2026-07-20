// src/main/java/com/shopnow/infrastructure/cache/RedisRefreshTokenStore.java
package com.shopnow.infrastructure.cache;

import com.shopnow.domain.port.RefreshTokenStore;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String PREFIX = "refresh:";

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisRefreshTokenStore(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void store(String jti, Long userId, Duration ttl) {
        redisTemplate.opsForValue().set(PREFIX + jti, userId, ttl);
    }

    @Override
    public boolean exists(String jti) {
        Boolean present = redisTemplate.hasKey(PREFIX + jti);
        return Boolean.TRUE.equals(present);
    }

    @Override
    public void revoke(String jti) {
        redisTemplate.delete(PREFIX + jti);
    }
}
