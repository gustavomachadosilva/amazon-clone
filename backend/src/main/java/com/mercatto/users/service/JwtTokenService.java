package com.mercatto.users.service;

import com.mercatto.users.domain.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;

/**
 * Only class in the codebase that imports {@code io.jsonwebtoken.*}. Everything else — including
 * the JWT authentication filter in {@code com.mercatto.config} — depends on {@link TokenService}
 * only, per the "integrations are ports" rule.
 */
@Service
class JwtTokenService implements TokenService {

    private static final String ROLE_CLAIM = "role";

    private final SecretKey signingKey;
    private final long expirationMs;

    JwtTokenService(@Value("${jwt.secret}") String secret, @Value("${jwt.expiration-ms}") long expirationMs) {
        // HS256 requires a key of at least 256 bits. Rather than crash on boot in dev when
        // JWT_SECRET is short (e.g. the "replace-me" placeholder), the configured secret is
        // stretched into a fixed-size 256-bit key via SHA-256. In production a long, random
        // JWT_SECRET should still be used — this only guarantees the minimum key size.
        this.signingKey = Keys.hmacShaKeyFor(sha256(secret));
        this.expirationMs = expirationMs;
    }

    private static byte[] sha256(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    @Override
    public IssuedToken issue(Long userId, UserRole role) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(expirationMs);
        String token = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(ROLE_CLAIM, role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    @Override
    public AuthenticatedUser validate(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Long userId = Long.valueOf(claims.getSubject());
            UserRole role = UserRole.valueOf(claims.get(ROLE_CLAIM, String.class));
            return new AuthenticatedUser(userId, role);
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("Invalid or expired token: " + e.getMessage());
        }
    }
}
