package com.mercatto.orders.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Locale;

/**
 * Chooses the {@link PaymentGateway} implementation at startup: the real
 * Stripe adapter when a non-placeholder API key is configured, otherwise the
 * in-memory mock. Lives in {@code orders.service} (not {@code com.mercatto.config})
 * both because it needs package-private access to {@link MockPaymentGateway}
 * and {@link StripePaymentGateway}, and because business modules must not
 * depend on the config package.
 * <p>
 * {@code payment.mock.decline} ({@code none} | {@code always} | {@code first-attempt})
 * makes the mock decline deterministically (see {@link MockPaymentGateway.DeclineMode});
 * it is ignored when Stripe is active.
 */
@Configuration
@Slf4j
class PaymentGatewayConfig {

    private static final String PLACEHOLDER = "replace-me";

    @Bean
    PaymentGateway paymentGateway(
            @Value("${stripe.api-key:}") String stripeApiKey,
            @Value("${payment.mock.decline:none}") String mockDecline) {
        MockPaymentGateway.DeclineMode declineMode = parseDeclineMode(mockDecline);
        if (stripeApiKey == null || stripeApiKey.isBlank() || PLACEHOLDER.equals(stripeApiKey)) {
            return new MockPaymentGateway(declineMode);
        }
        if (declineMode != MockPaymentGateway.DeclineMode.NONE) {
            log.warn("payment.mock.decline={} is ignored: a Stripe API key is configured, so StripePaymentGateway is used",
                    mockDecline);
        }
        return new StripePaymentGateway(stripeApiKey);
    }

    static MockPaymentGateway.DeclineMode parseDeclineMode(String value) {
        if (value == null || value.isBlank()) {
            return MockPaymentGateway.DeclineMode.NONE;
        }
        String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            return MockPaymentGateway.DeclineMode.valueOf(normalized);
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("Invalid payment.mock.decline '" + value + "'; expected one of "
                    + Arrays.stream(MockPaymentGateway.DeclineMode.values())
                            .map(mode -> mode.name().toLowerCase(Locale.ROOT).replace('_', '-'))
                            .toList(),
                    unknown);
        }
    }
}
