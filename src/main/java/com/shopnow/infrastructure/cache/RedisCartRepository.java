package com.shopnow.infrastructure.cache;

import com.shopnow.domain.model.Cart;
import com.shopnow.domain.port.CartRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Repository
public class RedisCartRepository implements CartRepository {

    private static final String CART_KEY_PREFIX = "cart:";
    private static final long CART_TTL_DAYS = 7;

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisCartRepository(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<Cart> findByUserId(Long userId) {
        String key = CART_KEY_PREFIX + userId;
        Cart cart = (Cart) redisTemplate.opsForValue().get(key);
        return Optional.ofNullable(cart);
    }

    @Override
    public Cart save(Cart cart) {
        String key = CART_KEY_PREFIX + cart.getUserId();
        redisTemplate.opsForValue().set(key, cart, CART_TTL_DAYS, TimeUnit.DAYS);
        return cart;
    }

    @Override
    public void deleteByUserId(Long userId) {
        String key = CART_KEY_PREFIX + userId;
        redisTemplate.delete(key);
    }
}
