package com.mercatto.reviews.service;

/**
 * Thrown when an uploaded review photo/video breaks an upload rule (too many files, disallowed
 * type, over the size limit). Mapped to 400 by the global exception handler.
 */
public class InvalidReviewMediaException extends RuntimeException {

    public InvalidReviewMediaException(String message) {
        super(message);
    }
}
