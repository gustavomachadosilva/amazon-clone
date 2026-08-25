package com.mercatto.orders.service;

import java.math.BigDecimal;

/**
 * Port for a third-party payment provider (e.g. Stripe). Swap the
 * {@link MockPaymentGateway} bean for a real adapter without touching any
 * Orders business logic — {@link com.mercatto.orders.service.OrderServiceImpl}
 * only ever depends on this interface.
 */
public interface PaymentGateway {

    PaymentResult charge(Long orderId, BigDecimal amount, String currency);

    record PaymentResult(boolean approved, String transactionId, String message) {}
}
