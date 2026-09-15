package com.mercatto.orders.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Calls the {@code @Bean} factory method directly as a plain Java method —
 * no Spring context involved — so this stays fast and offline.
 */
class PaymentGatewayConfigTest {

    private final PaymentGatewayConfig config = new PaymentGatewayConfig();

    @Test
    void emptyApiKeyYieldsMockGateway() {
        assertThat(config.paymentGateway("")).isInstanceOf(MockPaymentGateway.class);
    }

    @Test
    void placeholderApiKeyYieldsMockGateway() {
        assertThat(config.paymentGateway("replace-me")).isInstanceOf(MockPaymentGateway.class);
    }

    @Test
    void realApiKeyYieldsStripeGateway() {
        assertThat(config.paymentGateway("sk_test_fake_key_for_unit_test"))
                .isInstanceOf(StripePaymentGateway.class);
    }
}
