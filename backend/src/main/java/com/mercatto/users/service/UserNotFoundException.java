package com.mercatto.users.service;

/**
 * Thrown by {@link UserService#updateProfile} and {@link UserService#changePassword} when no user exists with the given id. Raised
 * before anything is persisted.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String message) {
        super(message);
    }
}
