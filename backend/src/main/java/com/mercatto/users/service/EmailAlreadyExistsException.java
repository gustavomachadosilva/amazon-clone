package com.mercatto.users.service;

/**
 * Thrown by {@link UserService#register} when the given email is already associated with an
 * existing account. Raised before any user is persisted.
 */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String message) {
        super(message);
    }
}
