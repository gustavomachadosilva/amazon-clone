package com.mercatto.lists.service;

/**
 * Thrown when an operation references a wish list id that does not exist, or that does
 * not belong to the requesting buyer. The same exception (and 404 status) is used for
 * both cases so a request never reveals whether another buyer's list exists.
 */
public class WishListNotFoundException extends RuntimeException {

    public WishListNotFoundException(String message) {
        super(message);
    }
}
