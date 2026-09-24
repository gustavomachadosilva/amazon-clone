package com.mercatto.users.service;

/**
 * Thrown by {@link UserService#register} and {@link UserService#updateProfile} when the given
 * email is already associated with another existing account. Raised before anything is persisted.
 */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String message) {
        super(message);
    }
}
