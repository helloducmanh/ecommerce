package com.shopnow.application.auth;

import com.shopnow.domain.model.EmailAlreadyExistsException;
import com.shopnow.domain.model.InvalidCredentialsException;
import com.shopnow.domain.model.User;
import com.shopnow.domain.port.RefreshTokenStore;
import com.shopnow.domain.port.UserRepository;
import com.shopnow.infrastructure.security.JwtProperties;
import com.shopnow.infrastructure.security.JwtService;
import com.shopnow.presentation.dto.AuthResponse;
import com.shopnow.presentation.dto.LoginRequest;
import com.shopnow.presentation.dto.RefreshRequest;
import com.shopnow.presentation.dto.RegisterRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenStore refreshTokenStore;
    private final JwtProperties jwtProperties;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenStore refreshTokenStore,
                       JwtProperties jwtProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenStore = refreshTokenStore;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }
        User user = new User(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.firstName(),
                request.lastName()
        );
        User saved = userRepository.save(user);
        return issueTokens(saved);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
        return issueTokens(user);
    }

    public AuthResponse refresh(RefreshRequest request) {
        Jwt jwt;
        try {
            jwt = jwtService.decode(request.refreshToken());
        } catch (JwtException e) {
            throw new InvalidCredentialsException("Invalid refresh token");
        }
        if (!"refresh".equals(jwt.getClaim("type"))) {
            throw new InvalidCredentialsException("Invalid refresh token");
        }
        String jti = jwt.getClaim("jti");
        if (!refreshTokenStore.exists(jti)) {
            throw new InvalidCredentialsException("Refresh token revoked");
        }
        refreshTokenStore.revoke(jti);
        Long userId = Long.valueOf(jwt.getSubject());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));
        return issueTokens(user);
    }

    public void logout(RefreshRequest request) {
        try {
            Jwt jwt = jwtService.decode(request.refreshToken());
            String jti = jwt.getClaim("jti");
            if (jti != null) {
                refreshTokenStore.revoke(jti);
            }
        } catch (JwtException ignored) {
            // Treat an invalid token as already logged out.
        }
    }

    private AuthResponse issueTokens(User user) {
        JwtService.TokenPair pair = jwtService.generateTokens(user);
        refreshTokenStore.store(
                pair.refreshJti(),
                user.getId(),
                Duration.ofSeconds(jwtProperties.refreshTokenExpiration())
        );
        return new AuthResponse(
                pair.accessToken(),
                pair.refreshToken(),
                "Bearer",
                pair.expiresIn()
        );
    }
}
