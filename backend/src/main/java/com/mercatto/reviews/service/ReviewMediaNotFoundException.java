package com.mercatto.reviews.service;

/**
 * Thrown when a review media id (or its stored file) does not exist. Mapped to 404.
 */
public class ReviewMediaNotFoundException extends RuntimeException {

    public ReviewMediaNotFoundException(String message) {
        super(message);
    }
}
