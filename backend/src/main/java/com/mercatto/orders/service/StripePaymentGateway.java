package com.mercatto.orders.service;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Real {@link PaymentGateway} adapter backed by Stripe's PaymentIntents API
 * (the Charges API is blocked for new Stripe accounts). Confirms the intent
 * synchronously in a single call, so no requires_action/3DS flow is handled.
 */
class StripePaymentGateway implements PaymentGateway {

    private final String apiKey;

    StripePaymentGateway(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public PaymentResult charge(Long orderId, BigDecimal amount, String currency) {
        try {
            RequestOptions options = RequestOptions.builder().setApiKey(apiKey).build();
            long amountInCents = amount.setScale(2, RoundingMode.HALF_UP)
                    .movePointRight(2)
                    .longValueExact();
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency(currency.toLowerCase(Locale.ROOT))
                    // pm_card_visa is Stripe's ready-made test PaymentMethod for
                    // server-side testing; used here because there is no
                    // frontend tokenization (Stripe Elements) yet — a known,
                    // accepted limitation for this academic project.
                    .setPaymentMethod("pm_card_visa")
                    .addPaymentMethodType("card")
                    .setConfirm(true)
                    .putMetadata("orderId", String.valueOf(orderId))
                    .build();
            PaymentIntent intent = PaymentIntent.create(params, options);
            boolean approved = "succeeded".equals(intent.getStatus());
            return new PaymentResult(approved, intent.getId(),
                    approved ? "Approved by Stripe" : "Stripe status: " + intent.getStatus());
        } catch (StripeException e) {
            return new PaymentResult(false, null, "Stripe error: " + e.getMessage());
        }
    }
}
