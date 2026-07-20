package com.shopnow.infrastructure.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

public record UserPrincipal(Long userId, String email, String role) {

    public Collection<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
