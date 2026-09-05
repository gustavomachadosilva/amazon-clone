package com.mercatto.users.service;

import com.mercatto.users.domain.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit test (no Spring context) for {@link JwtTokenService}, the only class in the codebase
 * allowed to import the jjwt library directly.
 */
class JwtTokenServiceTest {

    private static final String SECRET = "unit-test-secret";
    private static final long ONE_DAY_MS = 86_400_000L;

    @Test
    void issueThenValidate_roundTripsUserIdAndRole() {
        JwtTokenService tokenService = new JwtTokenService(SECRET, ONE_DAY_MS);

        TokenService.IssuedToken issued = tokenService.issue(42L, UserRole.SELLER);
        AuthenticatedUser authenticatedUser = tokenService.validate(issued.value());

        assertThat(authenticatedUser.userId()).isEqualTo(42L);
        assertThat(authenticatedUser.role()).isEqualTo(UserRole.SELLER);
    }

    @Test
    void validate_withTokenSignedByDifferentSecret_throwsInvalidTokenException() {
        JwtTokenService issuer = new JwtTokenService(SECRET, ONE_DAY_MS);
        JwtTokenService validator = new JwtTokenService("a-completely-different-secret", ONE_DAY_MS);

        TokenService.IssuedToken issued = issuer.issue(1L, UserRole.BUYER);

        assertThatThrownBy(() -> validator.validate(issued.value()))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void validate_withExpiredToken_throwsInvalidTokenException() {
        JwtTokenService tokenService = new JwtTokenService(SECRET, -1_000L);

        TokenService.IssuedToken issued = tokenService.issue(1L, UserRole.BUYER);

        assertThatThrownBy(() -> tokenService.validate(issued.value()))
                .isInstanceOf(InvalidTokenException.class);
    }
}
