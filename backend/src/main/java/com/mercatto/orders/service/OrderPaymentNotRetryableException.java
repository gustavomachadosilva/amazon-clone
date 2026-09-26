package com.mercatto.orders.service;

/**
 * Thrown by {@link OrderService#retryPayment} when the order's payment cannot be retried: it is
 * not FAILED (e.g. already PAID, or still PENDING/PROCESSING), or a concurrent retry of the same
 * order already claimed it for charging. Mapped to HTTP 409.
 */
public class OrderPaymentNotRetryableException extends RuntimeException {

    public OrderPaymentNotRetryableException(String message) {
        super(message);
    }
}
