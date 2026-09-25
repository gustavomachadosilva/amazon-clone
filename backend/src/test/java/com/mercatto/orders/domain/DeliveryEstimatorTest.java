package com.mercatto.orders.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryEstimatorTest {

    // 15:00 UTC = 12:00 in São Paulo, so these instants fall on the named local day.
    private static final Instant MONDAY = Instant.parse("2026-09-28T15:00:00Z");
    private static final Instant TUESDAY = Instant.parse("2026-09-29T15:00:00Z");
    private static final Instant FRIDAY = Instant.parse("2026-09-25T15:00:00Z");
    private static final Instant SATURDAY = Instant.parse("2026-09-26T15:00:00Z");

    @Test
    void standardFromMondayAddsFiveBusinessDays() {
        assertThat(DeliveryEstimator.estimate(MONDAY, ShippingMethod.STANDARD)).isEqualTo(LocalDate.of(2026, 10, 5));
    }

    @Test
    void standardFromFridaySkipsTheWeekend() {
        assertThat(DeliveryEstimator.estimate(FRIDAY, ShippingMethod.STANDARD)).isEqualTo(LocalDate.of(2026, 10, 2));
    }

    @Test
    void standardFromSaturdayStartsCountingOnMonday() {
        assertThat(DeliveryEstimator.estimate(SATURDAY, ShippingMethod.STANDARD)).isEqualTo(LocalDate.of(2026, 10, 2));
    }

    @Test
    void expressFromFridayArrivesMonday() {
        assertThat(DeliveryEstimator.estimate(FRIDAY, ShippingMethod.EXPRESS)).isEqualTo(LocalDate.of(2026, 9, 28));
    }

    @Test
    void expressFromTuesdayArrivesWednesday() {
        assertThat(DeliveryEstimator.estimate(TUESDAY, ShippingMethod.EXPRESS)).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    void pickupIsEstimatedLikeStandard() {
        assertThat(DeliveryEstimator.estimate(MONDAY, ShippingMethod.PICKUP))
                .isEqualTo(DeliveryEstimator.estimate(MONDAY, ShippingMethod.STANDARD));
    }

    @Test
    void nullShippingMethodIsEstimatedLikeStandard() {
        assertThat(DeliveryEstimator.estimate(MONDAY, null))
                .isEqualTo(DeliveryEstimator.estimate(MONDAY, ShippingMethod.STANDARD));
    }

    @Test
    void nullCreatedAtHasNoEstimate() {
        assertThat(DeliveryEstimator.estimate(null, ShippingMethod.STANDARD)).isNull();
    }

    @Test
    void usesSaoPauloLocalDateNotUtc() {
        // Monday 01:30 UTC is still Sunday 22:30 in São Paulo, so counting starts from Sunday.
        Instant sundayEveningInSaoPaulo = Instant.parse("2026-09-28T01:30:00Z");

        assertThat(DeliveryEstimator.estimate(sundayEveningInSaoPaulo, ShippingMethod.STANDARD))
                .isEqualTo(LocalDate.of(2026, 10, 2));
    }
}
