package com.mercatto.orders.service;

/**
 * Thrown by {@link OrderService#updateShippingAddress} when the order can no longer have its
 * shipping address changed: it has already shipped (any {@link FulfillmentStatus} other than
 * NOT_SHIPPED) or it was CANCELLED. Mapped to HTTP 409.
 */
public class OrderAddressNotEditableException extends RuntimeException {

    public OrderAddressNotEditableException(String message) {
        super(message);
    }
}
