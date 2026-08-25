package com.mercatto.orders.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Stand-in for a real gateway (Stripe, etc). Always approves the charge so
 * the checkout flow can be exercised end-to-end before a real provider is
 * plugged in — replace this bean with a real {@link PaymentGateway}
 * implementation when that integration is ready.
 */
@Service
class MockPaymentGateway implements PaymentGateway {

    @Override
    public PaymentResult charge(Long orderId, BigDecimal amount, String currency) {
        return new PaymentResult(true, "mock_" + UUID.randomUUID(), "Approved by MockPaymentGateway");
    }
}
