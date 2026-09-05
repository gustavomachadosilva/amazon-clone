package com.mercatto.users.service;

/**
 * Thrown by {@link TokenService#validate} when the given token is missing, malformed, expired,
 * or fails signature verification.
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }
}
