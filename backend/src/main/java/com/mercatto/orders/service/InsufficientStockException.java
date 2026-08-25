package com.mercatto.orders.service;

/**
 * Thrown by {@link OrderService#checkout} when a requested quantity exceeds a
 * product's currently known stock. Raised before any payment is charged.
 */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String message) {
        super(message);
    }
}
