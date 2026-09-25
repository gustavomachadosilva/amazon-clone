package com.mercatto.orders.service;

/**
 * Thrown by {@link OrderService#advanceFulfillment} when the requested
 * {@link FulfillmentStatus} is not the immediate next state, or the order is not
 * PAID. Mapped to HTTP 409.
 */
public class InvalidFulfillmentTransitionException extends RuntimeException {

    public InvalidFulfillmentTransitionException(String message) {
        super(message);
    }
}
