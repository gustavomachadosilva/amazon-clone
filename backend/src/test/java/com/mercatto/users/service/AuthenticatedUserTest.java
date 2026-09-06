package com.mercatto.users.service;

import com.mercatto.users.domain.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit test (no Spring context) for {@link AuthenticatedUser}'s manual authorization
 * helpers, used by controllers instead of {@code @PreAuthorize}/AOP (not available: only
 * spring-security-crypto is on the classpath, not full Spring Security).
 */
class AuthenticatedUserTest {

    @Test
    void requireRole_withMatchingRole_doesNotThrow() {
        AuthenticatedUser seller = new AuthenticatedUser(1L, UserRole.SELLER);

        assertThatCode(() -> seller.requireRole(UserRole.SELLER)).doesNotThrowAnyException();
    }

    @Test
    void requireRole_withMismatchedRole_throwsForbiddenRoleException() {
        AuthenticatedUser buyer = new AuthenticatedUser(1L, UserRole.BUYER);

        assertThatThrownBy(() -> buyer.requireRole(UserRole.SELLER))
                .isInstanceOf(ForbiddenRoleException.class);
    }

    @Test
    void requireOwner_withMatchingId_doesNotThrow() {
        AuthenticatedUser user = new AuthenticatedUser(10L, UserRole.BUYER);

        assertThatCode(() -> user.requireOwner(10L)).doesNotThrowAnyException();
    }

    @Test
    void requireOwner_withDifferentId_throwsForbiddenRoleException() {
        AuthenticatedUser user = new AuthenticatedUser(10L, UserRole.BUYER);

        assertThatThrownBy(() -> user.requireOwner(99L))
                .isInstanceOf(ForbiddenRoleException.class);
    }
}
