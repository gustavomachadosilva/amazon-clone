package com.mercatto.orders.service;

/**
 * Thrown by {@link OrderService} operations that act on an order by id when no
 * such order exists. Mapped to HTTP 404.
 */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(String message) {
        super(message);
    }
}
