package com.mercatto.users.service;

import com.mercatto.users.domain.UserRole;

import java.security.Principal;

/**
 * Identity extracted from a validated JWT by {@link TokenService#validate}, carried on the
 * request as its {@link java.security.Principal} by the JWT authentication filter. Controllers
 * read {@code userId}/{@code role} from this instead of trusting a value supplied in the request
 * body or as a query/path parameter.
 */
public record AuthenticatedUser(Long userId, UserRole role) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(userId);
    }

    /**
     * Ensures this user has the {@code required} role, throwing {@link ForbiddenRoleException}
     * (mapped to HTTP 403) otherwise.
     */
    public void requireRole(UserRole required) {
        if (role != required) {
            throw new ForbiddenRoleException("Operação restrita a " + required);
        }
    }

    /**
     * Ensures this user is the owner of the resource identified by {@code resourceOwnerId},
     * throwing {@link ForbiddenRoleException} (mapped to HTTP 403) otherwise.
     */
    public void requireOwner(Long resourceOwnerId) {
        if (!userId.equals(resourceOwnerId)) {
            throw new ForbiddenRoleException("Você só pode acessar seus próprios recursos");
        }
    }
}
