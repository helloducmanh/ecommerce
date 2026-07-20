package com.shopnow.domain.port;

import com.shopnow.domain.model.Cart;
import java.util.Optional;

public interface CartRepository {
    Optional<Cart> findByUserId(Long userId);
    Cart save(Cart cart);
    void deleteByUserId(Long userId);
}
