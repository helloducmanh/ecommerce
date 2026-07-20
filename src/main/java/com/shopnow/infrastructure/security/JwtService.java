// src/main/java/com/shopnow/infrastructure/security/JwtService.java
package com.shopnow.infrastructure.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.shopnow.domain.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.OAuth2Error;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String ISSUER = "shopnow";

    private final JwtEncoder encoder;
    private final NimbusJwtDecoder decoder;
    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        if ("dev-only-secret-change-me-in-production-32-bytes-min".equals(properties.secret())) {
            log.warn("JWT secret is the default dev value — set the JWT_SECRET environment variable in production.");
        }
        if (properties.secret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT secret must be at least 256 bits (32 bytes)");
        }
        SecretKey key = secretKey(properties.secret());
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(key)
                .keyID("shopnow-hmac")
                .algorithm(com.nimbusds.jose.JWSAlgorithm.HS256)
                .build();
        this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
        OAuth2TokenValidator<Jwt> validator = token -> {
            Instant expiration = token.getExpiresAt();
            if (expiration != null && Instant.now().isAfter(expiration)) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token expired", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
        this.decoder = (NimbusJwtDecoder) NimbusJwtDecoder.withSecretKey(key).build();
        this.decoder.setJwtValidator(validator);
    }

    public TokenPair generateTokens(User user) {
        Instant now = Instant.now();
        String accessJti = UUID.randomUUID().toString();
        String refreshJti = UUID.randomUUID().toString();

        JwtClaimsSet accessClaims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(properties.accessTokenExpiration()))
                .subject(String.valueOf(user.getId()))
                .claim("type", "access")
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("jti", accessJti)
                .build();
        String accessToken = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), accessClaims)).getTokenValue();

        JwtClaimsSet refreshClaims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(properties.refreshTokenExpiration()))
                .subject(String.valueOf(user.getId()))
                .claim("type", "refresh")
                .claim("jti", refreshJti)
                .build();
        String refreshToken = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), refreshClaims)).getTokenValue();

        return new TokenPair(accessToken, refreshToken, accessJti, refreshJti, properties.accessTokenExpiration());
    }

    public Jwt decode(String token) {
        return decoder.decode(token);
    }

    private static SecretKey secretKey(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public record TokenPair(String accessToken, String refreshToken, String accessJti, String refreshJti, long expiresIn) {
    }
}
