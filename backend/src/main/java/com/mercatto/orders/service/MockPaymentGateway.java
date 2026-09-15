package com.mercatto.orders.service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Stand-in for a real gateway (Stripe, etc). Always approves the charge so
 * the checkout flow can be exercised end-to-end when no real provider is
 * configured. Instantiated directly by {@link PaymentGatewayConfig} — not a
 * {@code @Service} itself, to avoid a second competing {@link PaymentGateway}
 * bean alongside {@link StripePaymentGateway}.
 */
class MockPaymentGateway implements PaymentGateway {

    @Override
    public PaymentResult charge(Long orderId, BigDecimal amount, String currency) {
        return new PaymentResult(true, "mock_" + UUID.randomUUID(), "Approved by MockPaymentGateway");
    }
}
