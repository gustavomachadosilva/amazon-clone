package com.mercatto.users.service;

/**
 * Thrown when an authenticated user's {@link com.mercatto.users.domain.UserRole} does not have
 * permission to perform the requested operation (e.g. a buyer trying to create a product).
 */
public class ForbiddenRoleException extends RuntimeException {

    public ForbiddenRoleException(String message) {
        super(message);
    }
}
