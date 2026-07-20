// src/main/java/com/shopnow/domain/port/RefreshTokenStore.java
package com.shopnow.domain.port;

import java.time.Duration;

public interface RefreshTokenStore {
    void store(String jti, Long userId, Duration ttl);
    boolean exists(String jti);
    void revoke(String jti);

    /**
     * Atomically consume (revoke) a refresh token, returning whether it existed.
     * Use this for refresh-token rotation so two concurrent refresh requests with
     * the same valid token cannot both succeed.
     */
    boolean consume(String jti);
}
