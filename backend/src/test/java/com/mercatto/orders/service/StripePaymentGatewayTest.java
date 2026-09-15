package com.mercatto.orders.service;

import com.stripe.exception.ApiException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * {@link PaymentIntent#create} is mocked statically so this never leaves the
 * process — no real Stripe key or network call is used, even though a
 * plausible-looking test key string is passed around.
 */
class StripePaymentGatewayTest {

    private static final String FAKE_API_KEY = "sk_test_fake_key_for_unit_test";

    private final StripePaymentGateway gateway = new StripePaymentGateway(FAKE_API_KEY);

    @Test
    void chargeReturnsApprovedResultWhenIntentSucceeds() {
        PaymentIntent fakeIntent = new PaymentIntent();
        fakeIntent.setId("pi_fake_123");
        fakeIntent.setStatus("succeeded");

        try (MockedStatic<PaymentIntent> mocked = mockStatic(PaymentIntent.class)) {
            mocked.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class), any()))
                    .thenReturn(fakeIntent);

            PaymentGateway.PaymentResult result = gateway.charge(1L, BigDecimal.valueOf(99.90), "BRL");

            assertThat(result.approved()).isTrue();
            assertThat(result.transactionId()).isEqualTo("pi_fake_123");
            assertThat(result.message()).isEqualTo("Approved by Stripe");
        }
    }

    @Test
    void chargeReturnsDeclinedResultWhenIntentDoesNotSucceed() {
        PaymentIntent fakeIntent = new PaymentIntent();
        fakeIntent.setId("pi_fake_456");
        fakeIntent.setStatus("requires_action");

        try (MockedStatic<PaymentIntent> mocked = mockStatic(PaymentIntent.class)) {
            mocked.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class), any()))
                    .thenReturn(fakeIntent);

            PaymentGateway.PaymentResult result = gateway.charge(1L, BigDecimal.valueOf(50), "BRL");

            assertThat(result.approved()).isFalse();
            assertThat(result.message()).contains("requires_action");
        }
    }

    @Test
    void chargeReturnsDeclinedResultWhenStripeThrows() {
        ApiException fakeError = new ApiException("Simulated stripe failure", "req_fake", null, 500, null);

        try (MockedStatic<PaymentIntent> mocked = mockStatic(PaymentIntent.class)) {
            mocked.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class), any()))
                    .thenThrow(fakeError);

            PaymentGateway.PaymentResult result = gateway.charge(1L, BigDecimal.TEN, "BRL");

            assertThat(result.approved()).isFalse();
            assertThat(result.transactionId()).isNull();
            assertThat(result.message()).contains("Simulated stripe failure");
        }
    }
}
