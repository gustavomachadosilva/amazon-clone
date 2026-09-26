package com.mercatto.orders.service;

import java.util.Optional;

/**
 * Shipping lifecycle of a PAID order, independent of its payment {@link OrderStatus}.
 * Advances strictly forward, one step at a time:
 * NOT_SHIPPED → SHIPPED → OUT_FOR_DELIVERY → DELIVERED.
 */
public enum FulfillmentStatus {
    NOT_SHIPPED,
    SHIPPED,
    OUT_FOR_DELIVERY,
    DELIVERED;

    /** The immediate next state, or empty for the terminal {@link #DELIVERED}. */
    public Optional<FulfillmentStatus> next() {
        FulfillmentStatus[] values = values();
        return ordinal() + 1 < values.length ? Optional.of(values[ordinal() + 1]) : Optional.empty();
    }

    /** Only the immediate next state is a valid transition (no skipping, no going back). */
    public boolean canAdvanceTo(FulfillmentStatus target) {
        return target != null && next().filter(target::equals).isPresent();
    }
}
