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
}
