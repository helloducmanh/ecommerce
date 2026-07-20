package com.shopnow.infrastructure.cache;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises {@link RedisRefreshTokenStore} against a real Redis 7 instance via Testcontainers,
 * proving the atomic consume, revocation, and TTL semantics that the Mockito-based unit test
 * cannot verify.
 */
@Testcontainers
class RedisRefreshTokenStoreIntegrationTest {

    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(REDIS_IMAGE).withExposedPorts(6379);

    private RedisRefreshTokenStore newStore() {
        RedisStandaloneConfiguration cfg = new RedisStandaloneConfiguration(
                redis.getHost(), redis.getMappedPort(6379));
        LettuceConnectionFactory factory = new LettuceConnectionFactory(cfg);
        factory.afterPropertiesSet();

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();

        return new RedisRefreshTokenStore(template);
    }

    @Test
    void storeThenExistsReturnsTrue() {
        RedisRefreshTokenStore store = newStore();

        store.store("jti-store", 7L, Duration.ofSeconds(60));

        assertTrue(store.exists("jti-store"));
    }

    @Test
    void consumeOnExistingReturnsTrueAndRemovesToken() {
        RedisRefreshTokenStore store = newStore();
        store.store("jti-consume", 7L, Duration.ofSeconds(60));

        boolean consumed = store.consume("jti-consume");

        assertTrue(consumed, "consume on a stored token should return true");
        assertFalse(store.exists("jti-consume"), "token must be gone after consume");
    }

    @Test
    void consumeOnMissingReturnsFalse() {
        RedisRefreshTokenStore store = newStore();

        boolean consumed = store.consume("never-stored");

        assertFalse(consumed, "consume on a missing token should return false");
    }

    @Test
    void consumeIsAtomicOnlyFirstConsumeWins() {
        RedisRefreshTokenStore store = newStore();
        store.store("jti-race", 7L, Duration.ofSeconds(60));

        boolean first = store.consume("jti-race");
        boolean second = store.consume("jti-race");

        assertTrue(first, "first consume must succeed");
        assertFalse(second, "second consume of the same token must fail (atomic rotation)");
    }

    @Test
    void revokeRemovesToken() {
        RedisRefreshTokenStore store = newStore();
        store.store("jti-revoke", 7L, Duration.ofSeconds(60));

        store.revoke("jti-revoke");

        assertFalse(store.exists("jti-revoke"));
    }

    @Test
    void storedTokenExpiresAfterTtl() throws InterruptedException {
        RedisRefreshTokenStore store = newStore();

        store.store("jti-ttl", 7L, Duration.ofSeconds(1));

        assertTrue(store.exists("jti-ttl"));
        // Wait past the TTL plus a margin for Redis to evict.
        Thread.sleep(1500);
        assertFalse(store.exists("jti-ttl"), "token should be evicted after its TTL");
    }
}
