package com.mercatto.orders.service;

/**
 * Thrown when a user tries to act on an order they have no stake in, e.g. a
 * seller advancing the fulfillment of an order containing none of their items.
 * Mapped to HTTP 403.
 */
public class OrderAccessDeniedException extends RuntimeException {

    public OrderAccessDeniedException(String message) {
        super(message);
    }
}
