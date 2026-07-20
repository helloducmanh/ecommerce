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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String SECRET = "test-secret-at-least-32-bytes-long-padding-padding";

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        jwtService = new JwtService(new JwtProperties(SECRET, 900, 604800));
        authService = new AuthService(userRepository, passwordEncoder, jwtService, refreshTokenStore,
                new JwtProperties(SECRET, 900, 604800));
    }

    private User persistedUser() {
        // Simulate JPA assigning an id on save.
        return new User("alice@example.com", passwordEncoder.encode("password123"), "Alice", "Smith") {{
            try {
                var f = User.class.getDeclaredField("id");
                f.setAccessible(true);
                f.set(this, 1L);
            } catch (Exception ignored) {
            }
        }};
    }

    @Test
    void shouldRegisterNewUser() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            var f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(u, 1L);
            return u;
        });

        AuthResponse response = authService.register(
                new RegisterRequest("alice@example.com", "password123", "Alice", "Smith"));

        assertNotNull(response.accessToken());
        assertNotNull(response.refreshToken());
        verify(refreshTokenStore).store(any(String.class), eq(1L), any(Duration.class));
    }

    @Test
    void shouldRejectDuplicateEmailOnRegister() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () ->
                authService.register(new RegisterRequest("alice@example.com", "password123", "Alice", "Smith")));
    }

    @Test
    void shouldLoginWithValidCredentials() {
        User user = persistedUser();
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        AuthResponse response = authService.login(new LoginRequest("alice@example.com", "password123"));

        assertNotNull(response.accessToken());
        verify(refreshTokenStore).store(any(String.class), eq(1L), any(Duration.class));
    }

    @Test
    void shouldRejectLoginWithWrongPassword() {
        User user = persistedUser();
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        assertThrows(InvalidCredentialsException.class, () ->
                authService.login(new LoginRequest("alice@example.com", "wrong-password")));
    }

    @Test
    void shouldRejectLoginWithUnknownEmail() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () ->
                authService.login(new LoginRequest("nobody@example.com", "password123")));
    }

    @Test
    void shouldRotateRefreshToken() {
        User user = persistedUser();
        AuthResponse first = jwtLogin(user);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(refreshTokenStore.exists(any(String.class))).thenReturn(true);

        AuthResponse rotated = authService.refresh(new RefreshRequest(first.refreshToken()));

        assertNotEquals(first.refreshToken(), rotated.refreshToken());
        verify(refreshTokenStore).revoke(any(String.class)); // old jti revoked
        verify(refreshTokenStore, times(2)).store(any(String.class), eq(1L), any(Duration.class));
    }

    @Test
    void shouldRejectRefreshForRevokedToken() {
        User user = persistedUser();
        AuthResponse tokens = jwtLogin(user);

        when(refreshTokenStore.exists(any(String.class))).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () ->
                authService.refresh(new RefreshRequest(tokens.refreshToken())));
    }

    @Test
    void shouldLogoutByRevokingRefreshToken() {
        User user = persistedUser();
        AuthResponse tokens = jwtLogin(user);

        authService.logout(new RefreshRequest(tokens.refreshToken()));

        verify(refreshTokenStore).revoke(any(String.class));
    }

    private AuthResponse jwtLogin(User user) {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        return authService.login(new LoginRequest("alice@example.com", "password123"));
    }
}
