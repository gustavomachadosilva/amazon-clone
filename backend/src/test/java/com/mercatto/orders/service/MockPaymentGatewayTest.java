package com.mercatto.orders.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MockPaymentGatewayTest {

    private static PaymentGateway.PaymentResult charge(MockPaymentGateway gateway, long orderId) {
        return gateway.charge(orderId, BigDecimal.TEN, "BRL");
    }

    @Test
    void noArgConstructorApprovesEveryCharge() {
        MockPaymentGateway gateway = new MockPaymentGateway();

        assertThat(charge(gateway, 1L).approved()).isTrue();
        assertThat(charge(gateway, 1L).approved()).isTrue();
    }

    @Test
    void noneApprovesEveryChargeWithATransactionId() {
        MockPaymentGateway gateway = new MockPaymentGateway(MockPaymentGateway.DeclineMode.NONE);

        PaymentGateway.PaymentResult result = charge(gateway, 1L);

        assertThat(result.approved()).isTrue();
        assertThat(result.transactionId()).startsWith("mock_");
        assertThat(charge(gateway, 1L).approved()).isTrue();
    }

    @Test
    void alwaysDeclinesEveryCharge() {
        MockPaymentGateway gateway = new MockPaymentGateway(MockPaymentGateway.DeclineMode.ALWAYS);

        PaymentGateway.PaymentResult first = charge(gateway, 1L);

        assertThat(first.approved()).isFalse();
        assertThat(first.transactionId()).isNull();
        assertThat(first.message()).contains("payment.mock.decline=always");
        assertThat(charge(gateway, 1L).approved()).isFalse();
        assertThat(charge(gateway, 2L).approved()).isFalse();
    }

    @Test
    void firstAttemptDeclinesOnlyTheFirstChargeOfEachOrder() {
        MockPaymentGateway gateway = new MockPaymentGateway(MockPaymentGateway.DeclineMode.FIRST_ATTEMPT);

        PaymentGateway.PaymentResult firstOfOrder1 = charge(gateway, 1L);
        assertThat(firstOfOrder1.approved()).isFalse();
        assertThat(firstOfOrder1.message()).contains("payment.mock.decline=first-attempt");

        assertThat(charge(gateway, 1L).approved()).isTrue();
        assertThat(charge(gateway, 1L).approved()).isTrue();

        // Tracked per order: another order's first charge is declined too.
        assertThat(charge(gateway, 2L).approved()).isFalse();
        assertThat(charge(gateway, 2L).approved()).isTrue();
    }
}
