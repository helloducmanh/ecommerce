package com.shopnow.domain.port;

public interface OrderQueryPort {
    boolean hasUserPurchasedProduct(Long userId, Long productId);
}
