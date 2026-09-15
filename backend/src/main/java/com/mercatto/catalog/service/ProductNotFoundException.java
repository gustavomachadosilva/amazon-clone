package com.mercatto.catalog.service;

/**
 * Thrown when an operation references a product id that does not exist in the
 * catalog (e.g. update, delete or stock decrement on an unknown product).
 */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(String message) {
        super(message);
    }
}
