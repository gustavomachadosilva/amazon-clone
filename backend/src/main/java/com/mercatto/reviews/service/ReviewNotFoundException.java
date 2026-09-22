package com.mercatto.reviews.service;

/**
 * Thrown when an operation references a review id that does not exist (e.g. marking an
 * unknown review as helpful).
 */
public class ReviewNotFoundException extends RuntimeException {

    public ReviewNotFoundException(String message) {
        super(message);
    }
}
