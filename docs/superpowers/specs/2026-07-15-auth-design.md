# ShopNow JWT Authentication Design

**Date:** 2026-07-15
**Phase:** Post-MVP — User Authentication & Authorization
**Status:** Draft

## 1. Overview

This phase adds JWT-based authentication and role-based authorization to the ShopNow MVP. It introduces user accounts, access/refresh token pairs, and ownership checks so users can only access their own resources.

**Scope:**
- User registration and login
- JWT access tokens (15 min) + refresh tokens (7 days, Redis allowlist)
- BCrypt password hashing
- Role-based access control (CUSTOMER, ADMIN) via `@PreAuthorize`
- Ownership checks for cart and orders
- Update cart/order endpoints to use authenticated user instead of `userId` parameter

**Out of scope (deferred):**
- Rate limiting
- Password reset / forgot-password
- OAuth / social login
- Audit logging
- Account suspension enforcement

## 2. Architecture

Auth follows the existing layered monolith pattern: presentation → application → domain ← infrastructure.

### New Components

| Layer | Class | Responsibility |
|-------|-------|----------------|
| Domain | `User` entity | id, email, passwordHash, firstName, lastName, role, status |
| Domain | `UserRepository` port | findByEmail, findById, save, existsByEmail |
| Domain | `RefreshTokenStore` port | store(jti, userId, expiry), exists(jti), revoke(jti) |
| Infrastructure | `UserJpaRepository` + `UserRepositoryImpl` | JPA |
| Infrastructure | `RedisRefreshTokenStore` | Redis-backed allowlist (jti → userId, TTL 7d) |
| Application | `AuthService` | register, login, refresh, logout orchestration |
| Infrastructure | `JwtService` | sign/verify access + refresh tokens (Nimbus via Spring Security) |
| Infrastructure | `JwtAuthenticationFilter` | extracts Bearer token, validates, sets `SecurityContext` |
| Infrastructure | `SecurityConfig` (updated) | adds JWT filter, configures public vs protected endpoints |
| Presentation | `AuthController` | `/api/v1/auth/{register,login,refresh,logout}` |
| Presentation | `OrderSecurity` bean | `isOwner(orderId, auth)` for order ownership checks |

> **Cart ownership is implicit:** the cart is always keyed by `principal.userId`, so no ownership bean is needed — the controller simply passes `principal.userId` to the service. Only orders (which are addressed by `orderId` in the URL) require an explicit ownership check.

### Security Model

**Endpoint access rules:**
- `permitAll`: `/api/v1/auth/**`, GET `/api/v1/products/**`, GET `/api/v1/categories/**`
- `hasRole('CUSTOMER')`: `/api/v1/cart/**`, `/api/v1/orders/**`
- `hasRole('ADMIN')`: `/api/v1/admin/**`

**Cart/order controllers:** remove `userId` parameter → resolve from `@AuthenticationPrincipal UserPrincipal`.

**Services:** keep `userId` parameter (controllers translate `UserPrincipal → userId`). Services stay decoupled from Spring Security.

## 3. Token Flow

### Login

1. `POST /api/v1/auth/login` with `{email, password}`
2. `AuthService` looks up user by email → verifies bcrypt hash
3. If valid: generate **access token** (15 min, HMAC-SHA256) with claims: `sub` (userId), `email`, `role`, `jti` (UUID)
4. Generate **refresh token** (7 days) with claims: `sub` (userId), `jti` (UUID), `type: "refresh"`
5. Store refresh token's `jti` in Redis: key `refresh:{jti}` → value `userId`, TTL 7 days
6. Return `{access_token, refresh_token, token_type: "Bearer", expires_in: 900}`

### Request Authentication

1. `JwtAuthenticationFilter` runs before `UsernamePasswordAuthenticationFilter`
2. Extracts `Authorization: Bearer <token>` header
3. Validates signature + expiry using `JwtDecoder` (Spring Security)
4. Extracts claims → builds `UserPrincipal(userId, email, role)`
5. Sets `SecurityContextHolder.getContext().setAuthentication(...)`
6. Downstream: `@AuthenticationPrincipal UserPrincipal principal` injects the principal

### Refresh

1. `POST /api/v1/auth/refresh` with `{refresh_token}`
2. Decode refresh token → extract `jti`, `userId`
3. Check Redis: does `refresh:{jti}` exist? If no → 401 (revoked/expired)
4. If yes: revoke old `jti` (delete from Redis), issue new access + refresh tokens (rotation)
5. Store new `jti` in Redis

### Logout

1. `POST /api/v1/auth/logout` with `{refresh_token}`
2. Delete `refresh:{jti}` from Redis
3. Return 204

### Ownership Checks

- `@PreAuthorize("@orderSecurity.isOwner(#orderId, authentication)")` on `GET /orders/{orderId}`
- `OrderSecurity.isOwner(orderId, auth)` → load order → compare `order.userId` with `principal.userId`
- Cart does not need an ownership check — it is always keyed by `principal.userId`

### Error Responses

- Invalid credentials → 401 `{error: {code: "INVALID_CREDENTIALS", message: "..."}}`
- Expired/invalid token → 401
- Insufficient role → 403
- Token missing → 401

## 4. Data Model

### User Entity

```java
@Entity @Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String passwordHash;  // bcrypt

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.CUSTOMER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public enum UserRole { CUSTOMER, ADMIN }
    public enum UserStatus { ACTIVE, SUSPENDED }
}
```

### Refresh Token (Redis Only)

- Key: `refresh:{jti}` → Value: `{userId}` — TTL 7 days
- Single hash per user for fast lookup; jti is the revocation handle

### Configuration

```yaml
app:
  security:
    jwt:
      secret: ${JWT_SECRET:default-dev-only-key-256-bit-min-replace-in-prod}
      access-token-expiration: 900      # 15 min (seconds)
      refresh-token-expiration: 604800  # 7 days (seconds)
```

- Secret read from env var `JWT_SECRET` with a dev-only default (logged warning if default used)
- Expirations externalized for easy testing

### Flyway Migration

```sql
-- V004__create_users.sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
```

### Dependencies

Add to `pom.xml`:
- `spring-boot-starter-oauth2-resource-server` (brings Nimbus JOSE+JWT)

Already present: `spring-boot-starter-security`, `spring-boot-starter-data-redis`

### DTOs

```java
public record RegisterRequest(
    @Email @NotBlank String email,
    @NotBlank String password,
    @NotBlank String firstName,
    @NotBlank String lastName
) {}

public record LoginRequest(
    @Email @NotBlank String email,
    @NotBlank String password
) {}

public record RefreshRequest(
    @NotBlank String refreshToken
) {}

public record AuthResponse(
    String accessToken,
    String refreshToken,
    String tokenType,  // "Bearer"
    Long expiresIn
) {}

public record UserDto(
    Long id,
    String email,
    String firstName,
    String lastName,
    String role
) {}
```

### UserPrincipal

```java
public record UserPrincipal(Long userId, String email, String role) {
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
```

## 5. Testing Strategy

### Test Layers

| Layer | Test | Approach | Count |
|-------|------|----------|-------|
| Domain | `AuthServiceTest` | Mockito, mock repositories/stores | ~8 |
| Controller | `AuthControllerTest` | `@WebMvcTest` + MockMvc, mock AuthService | ~5 |
| Security | `SecurityIntegrationTest` | `@SpringBootTest` full context with mocked JWT | ~6 |
| Repository | `UserRepositoryTest` | `@DataJpaTest` + Testcontainers | ~3 |
| Ownership | `OrderSecurityTest` | Unit, mock repos | ~3 |

**Total:** ~26 new tests (35 existing + 26 new = ~61 total)

### Key Test Scenarios

**Auth:**
- Register → 201, password hashed (not plaintext stored)
- Register duplicate email → 409 Conflict
- Login valid → 200, tokens returned
- Login wrong password → 401
- Refresh valid → new token pair, old jti revoked
- Refresh with revoked/expired → 401
- Logout → refresh deleted from Redis

**Security filter:**
- Request with valid token → principal set, endpoint authorized
- Request with expired token → 401
- Request with malformed token → 401
- Request to protected endpoint without token → 401
- Customer accessing admin endpoint → 403

**RBAC:**
- `hasRole('ADMIN')` — only admin passes (existing AdminProductControllerTest updated)
- `hasRole('CUSTOMER')` — only authenticated customer passes
- Ownership: customer A can't fetch customer B's order → 403

**Existing tests updated:**
- `CartControllerTest`, `OrderControllerTest` — replace `userId` param mocking with `@WithMockUser` or mock `UserPrincipal`
- `AdminProductControllerTest` — already uses `@WithMockUser(roles="ADMIN")`, stays green

### Test Infrastructure

- Mock JWT signing in tests via a test `JwtDecoder` bean, or use a fixed test secret
- Redis tests: use `@WithMockUser` to bypass the filter for controller tests; for refresh-store tests, mock the `RefreshTokenStore` port
- Reuse the existing Testcontainers setup (PostgreSQL) for `UserRepositoryTest`

**Verification gate:** full `mvn test` must stay green (existing 35 tests + new ~26 = ~61 tests).

## 6. Migration Plan

### What Changes in Existing Code

| File | Change |
|------|--------|
| `CartController` | Remove `@RequestParam Long userId` → use `@AuthenticationPrincipal UserPrincipal` |
| `OrderController` | Same — userId from principal |
| `CartService` | Methods take `Long userId` (called from controller with principal's id) — service signatures unchanged |
| `OrderService` | Same |
| `SecurityConfig` | Replace `permitAll` for `/api/**` with proper role rules + add JWT filter |
| `AdminProductController` etc. | Already `@PreAuthorize("hasRole('ADMIN')")` — unchanged |

### Task Breakdown

1. **Task 1: User domain + repo** — User entity, UserRepository port, JPA impl, V004 migration, PasswordEncoder bean
2. **Task 2: JWT infrastructure** — JwtService (sign/verify), RefreshTokenStore port + Redis impl, properties config, pom dependency
3. **Task 3: Auth API** — AuthService, AuthController, DTOs, register/login/refresh/logout
4. **Task 4: Security wiring** — JwtAuthenticationFilter, updated SecurityConfig with filter chain + RBAC rules, UserPrincipal
5. **Task 5: Ownership checks** — OrderSecurity, CartSecurity beans + `@PreAuthorize` on controllers
6. **Task 6: Controller migration** — cart/order controllers use principal, update their tests
7. **Task 7: Tests** — full test coverage, ensure all existing tests still pass, commit + push

## 7. Risk Notes

- Existing tests use `@WithMockUser` for admin and anonymous for public. After this phase, public endpoints (products/categories GET) stay `permitAll`, so those tests remain valid.
- Cart/order tests must switch from `userId` param to mocked principal — handled in Task 6.
- Services keep `userId` parameter (controllers translate `UserPrincipal → userId`). Services stay decoupled from Spring Security (cleaner, testable, follows existing layered pattern). This matches the spec's dependency rule.

## 8. References

- System design spec: `docs/superpowers/specs/2026-07-15-shopnow-design.md` (sections 1365-1413)
- Spring Security OAuth2 Resource Server: https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html
- Nimbus JOSE+JWT: https://connect2id.com/products/nimbus-jose-jwt
