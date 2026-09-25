package com.mercatto.users.service;

/**
 * Thrown by {@link UserService#changePassword} when the informed current password does not match
 * the user's stored password. Raised before anything is persisted.
 *
 * <p>Extends {@link IllegalArgumentException} so it is mapped to 400 Bad Request — never 401,
 * which is reserved for an invalid/expired token and triggers an automatic logout on the frontend.
 */
public class InvalidCurrentPasswordException extends IllegalArgumentException {

    public InvalidCurrentPasswordException(String message) {
        super(message);
    }
}
