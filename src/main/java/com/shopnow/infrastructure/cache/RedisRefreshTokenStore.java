// src/main/java/com/shopnow/infrastructure/cache/RedisRefreshTokenStore.java
package com.shopnow.infrastructure.cache;

import com.shopnow.domain.port.RefreshTokenStore;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

@Repository
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String PREFIX = "refresh:";

    /**
     * Atomic delete-and-report-existence. DEL by itself is not atomic-with-read when
     * issued through a RedisTemplate (the exists check and the delete are two round
     * trips), so a Lua script is used to make consume vs. read a single atomic op.
     * Returns the number of keys deleted; {@code >= 1} means a token was consumed.
     */
    private static final String CONSUME_SCRIPT = "return redis.call('DEL', KEYS[1])";
    private static final DefaultRedisScript<Long> CONSUME_REDIS_SCRIPT =
            new DefaultRedisScript<>(CONSUME_SCRIPT, Long.class);

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

    @Override
    public boolean consume(String jti) {
        Long deleted = redisTemplate.execute(
                CONSUME_REDIS_SCRIPT,
                List.of(PREFIX + jti));
        return deleted != null && deleted >= 1;
    }
}
