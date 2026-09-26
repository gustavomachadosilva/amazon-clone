package com.mercatto.orders.service;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stand-in for a real gateway (Stripe, etc), so the checkout flow can be exercised end-to-end
 * when no real provider is configured. Approves every charge by default; a deterministic
 * {@link DeclineMode} (property {@code payment.mock.decline}, see README) makes it decline, so a
 * FAILED order and its payment retry ({@code POST /api/orders/{id}/payment}) can be reproduced
 * locally. Instantiated directly by {@link PaymentGatewayConfig} — not a {@code @Service} itself,
 * to avoid a second competing {@link PaymentGateway} bean alongside {@link StripePaymentGateway}.
 */
class MockPaymentGateway implements PaymentGateway {

    /** How the mock decides to decline. Values of the {@code payment.mock.decline} property. */
    enum DeclineMode {
        /** Approves every charge (default). */
        NONE,
        /** Declines every charge. */
        ALWAYS,
        /**
         * Declines the first charge of each order and approves the following ones, so checkout
         * ends FAILED and a payment retry succeeds. Remembered in memory, per JVM.
         */
        FIRST_ATTEMPT
    }

    private final DeclineMode declineMode;
    private final Set<Long> declinedOrderIds = ConcurrentHashMap.newKeySet();

    MockPaymentGateway() {
        this(DeclineMode.NONE);
    }

    MockPaymentGateway(DeclineMode declineMode) {
        this.declineMode = declineMode;
    }

    @Override
    public PaymentResult charge(Long orderId, BigDecimal amount, String currency) {
        switch (declineMode) {
            case ALWAYS -> {
                return declined("Declined by MockPaymentGateway (payment.mock.decline=always)");
            }
            case FIRST_ATTEMPT -> {
                if (declinedOrderIds.add(orderId)) {
                    return declined("Declined by MockPaymentGateway (payment.mock.decline=first-attempt); "
                            + "retry the payment to have it approved");
                }
            }
            case NONE -> {
                // approve below
            }
        }
        return new PaymentResult(true, "mock_" + UUID.randomUUID(), "Approved by MockPaymentGateway");
    }

    private static PaymentResult declined(String message) {
        return new PaymentResult(false, null, message);
    }
}
