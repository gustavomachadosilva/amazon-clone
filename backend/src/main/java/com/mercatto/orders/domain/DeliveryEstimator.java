package com.mercatto.orders.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Computes an order's estimated delivery date from when it was paid and its
 * shipping method, counting business days only (skips Sat/Sun, no holiday
 * calendar) — mirrors the frontend's {@code lib/deliveryDate.ts addBusinessDays}.
 */
public final class DeliveryEstimator {

    /** Dates are computed in the marketplace's local time, not UTC. */
    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private static final int EXPRESS_BUSINESS_DAYS = 1;
    private static final int STANDARD_BUSINESS_DAYS = 5;

    private DeliveryEstimator() {
    }

    /**
     * Returns {@code null} when {@code from} is unknown. A null (legacy) shipping
     * method and PICKUP are estimated like STANDARD.
     */
    public static LocalDate estimate(Instant from, ShippingMethod method) {
        if (from == null) {
            return null;
        }
        int businessDays = method == ShippingMethod.EXPRESS ? EXPRESS_BUSINESS_DAYS : STANDARD_BUSINESS_DAYS;
        LocalDate date = from.atZone(ZONE).toLocalDate();
        int added = 0;
        while (added < businessDays) {
            date = date.plusDays(1);
            DayOfWeek day = date.getDayOfWeek();
            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
                added++;
            }
        }
        return date;
    }
}
