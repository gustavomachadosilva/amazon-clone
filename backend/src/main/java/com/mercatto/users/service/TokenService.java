package com.mercatto.users.service;

import com.mercatto.users.domain.UserRole;

import java.time.Instant;

/**
 * Public API for issuing and validating authentication tokens. The concrete token format (JWT)
 * is an implementation detail of {@link JwtTokenService}; nothing outside this package should
 * import a JWT library directly.
 */
public interface TokenService {

    IssuedToken issue(Long userId, UserRole role);

    /**
     * Validates the given token, throwing {@link InvalidTokenException} if it is missing,
     * malformed, expired, or fails signature verification.
     */
    AuthenticatedUser validate(String token);

    record IssuedToken(String value, Instant expiresAt) {}
}
