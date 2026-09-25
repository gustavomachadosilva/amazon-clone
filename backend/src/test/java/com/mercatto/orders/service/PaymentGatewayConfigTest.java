package com.mercatto.orders.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Calls the {@code @Bean} factory method directly as a plain Java method —
 * no Spring context involved — so this stays fast and offline.
 */
class PaymentGatewayConfigTest {

    private final PaymentGatewayConfig config = new PaymentGatewayConfig();

    @Test
    void emptyApiKeyYieldsMockGateway() {
        assertThat(config.paymentGateway("", "none")).isInstanceOf(MockPaymentGateway.class);
    }

    @Test
    void placeholderApiKeyYieldsMockGateway() {
        assertThat(config.paymentGateway("replace-me", "none")).isInstanceOf(MockPaymentGateway.class);
    }

    @Test
    void realApiKeyYieldsStripeGateway() {
        assertThat(config.paymentGateway("sk_test_fake_key_for_unit_test", "none"))
                .isInstanceOf(StripePaymentGateway.class);
    }

    @Test
    void realApiKeyYieldsStripeGatewayEvenWhenAMockDeclineModeIsSet() {
        assertThat(config.paymentGateway("sk_test_fake_key_for_unit_test", "always"))
                .isInstanceOf(StripePaymentGateway.class);
    }

    @Test
    void mockDeclineAlwaysYieldsAMockThatDeclines() {
        PaymentGateway gateway = config.paymentGateway("", "always");

        assertThat(gateway).isInstanceOf(MockPaymentGateway.class);
        assertThat(gateway.charge(1L, BigDecimal.TEN, "BRL").approved()).isFalse();
    }

    @Test
    void defaultMockApproves() {
        assertThat(config.paymentGateway("", "none").charge(1L, BigDecimal.TEN, "BRL").approved()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "none, NONE",
            "'', NONE",
            "always, ALWAYS",
            "ALWAYS, ALWAYS",
            "first-attempt, FIRST_ATTEMPT",
            "First_Attempt, FIRST_ATTEMPT",
            "' first-attempt ', FIRST_ATTEMPT"
    })
    void parsesDeclineModeCaseInsensitively(String value, MockPaymentGateway.DeclineMode expected) {
        assertThat(PaymentGatewayConfig.parseDeclineMode(value)).isEqualTo(expected);
    }

    @Test
    void unknownDeclineModeFailsFast() {
        assertThatThrownBy(() -> config.paymentGateway("", "sometimes"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payment.mock.decline")
                .hasMessageContaining("first-attempt");
    }
}
