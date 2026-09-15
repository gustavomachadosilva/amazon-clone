package com.mercatto.orders.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses the {@link PaymentGateway} implementation at startup: the real
 * Stripe adapter when a non-placeholder API key is configured, otherwise the
 * in-memory mock. Lives in {@code orders.service} (not {@code com.mercatto.config})
 * both because it needs package-private access to {@link MockPaymentGateway}
 * and {@link StripePaymentGateway}, and because business modules must not
 * depend on the config package.
 */
@Configuration
class PaymentGatewayConfig {

    private static final String PLACEHOLDER = "replace-me";

    @Bean
    PaymentGateway paymentGateway(@Value("${stripe.api-key:}") String stripeApiKey) {
        if (stripeApiKey == null || stripeApiKey.isBlank() || PLACEHOLDER.equals(stripeApiKey)) {
            return new MockPaymentGateway();
        }
        return new StripePaymentGateway(stripeApiKey);
    }
}
