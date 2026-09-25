package com.mercatto.orders.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FulfillmentStatusTest {

    @Test
    void nextFollowsTheShippingLifecycleAndEndsAtDelivered() {
        assertThat(FulfillmentStatus.NOT_SHIPPED.next()).contains(FulfillmentStatus.SHIPPED);
        assertThat(FulfillmentStatus.SHIPPED.next()).contains(FulfillmentStatus.OUT_FOR_DELIVERY);
        assertThat(FulfillmentStatus.OUT_FOR_DELIVERY.next()).contains(FulfillmentStatus.DELIVERED);
        assertThat(FulfillmentStatus.DELIVERED.next()).isEmpty();
    }

    @Test
    void canAdvanceOnlyToTheImmediateNextState() {
        for (FulfillmentStatus current : FulfillmentStatus.values()) {
            for (FulfillmentStatus target : FulfillmentStatus.values()) {
                boolean isImmediateNext = target.ordinal() == current.ordinal() + 1;
                assertThat(current.canAdvanceTo(target))
                        .as("%s -> %s", current, target)
                        .isEqualTo(isImmediateNext);
            }
        }
        assertThat(FulfillmentStatus.NOT_SHIPPED.canAdvanceTo(null)).isFalse();
    }
}
