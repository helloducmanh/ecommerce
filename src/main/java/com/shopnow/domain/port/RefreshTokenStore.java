// src/main/java/com/shopnow/domain/port/RefreshTokenStore.java
package com.shopnow.domain.port;

import java.time.Duration;

public interface RefreshTokenStore {
    void store(String jti, Long userId, Duration ttl);
    boolean exists(String jti);
    void revoke(String jti);
}
