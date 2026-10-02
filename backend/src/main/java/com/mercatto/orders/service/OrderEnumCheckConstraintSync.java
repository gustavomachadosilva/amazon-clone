package com.mercatto.orders.service;

import com.mercatto.orders.domain.PaymentMethod;
import com.mercatto.orders.domain.ShippingMethod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Widens the {@code CHECK} constraints Hibernate generates for {@code orders.orders}' enum
 * columns when the Java enum has gained values since the table was created. With
 * {@code ddl-auto: update}, Hibernate writes those constraints only on CREATE TABLE and never
 * revisits them, so a database created before e.g. {@link OrderStatus#PROCESSING} existed
 * rejects every checkout's PENDING -> PROCESSING claim with a constraint violation (surfacing as
 * a 409) after the order was already reserved. Runs on every boot and is a no-op once each
 * constraint already accepts every enum value. Only ever widens: a constraint that is missing
 * altogether is left alone, and values no longer in the enum are dropped from the rewritten
 * constraint only when one is rewritten anyway.
 */
@Component
@Slf4j
@RequiredArgsConstructor
class OrderEnumCheckConstraintSync {

    static final String TABLE = "orders.orders";

    record EnumColumn(String column, Class<? extends Enum<?>> type) {
        String constraintName() {
            return "orders_" + column + "_check";
        }

        List<String> values() {
            return Arrays.stream(type.getEnumConstants()).map(Enum::name).toList();
        }
    }

    static final List<EnumColumn> ENUM_COLUMNS = List.of(
            new EnumColumn("status", OrderStatus.class),
            new EnumColumn("fulfillment_status", FulfillmentStatus.class),
            new EnumColumn("shipping_method", ShippingMethod.class),
            new EnumColumn("payment_method", PaymentMethod.class));

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void syncEnumCheckConstraints() {
        for (EnumColumn enumColumn : ENUM_COLUMNS) {
            try {
                syncConstraint(enumColumn);
            } catch (RuntimeException ex) {
                log.error("Could not sync check constraint {} on {}", enumColumn.constraintName(), TABLE, ex);
            }
        }
    }

    private void syncConstraint(EnumColumn enumColumn) {
        List<String> definitions = jdbcTemplate.queryForList(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid = to_regclass(?) AND conname = ?",
                String.class, TABLE, enumColumn.constraintName());
        if (definitions.isEmpty()) {
            return;
        }
        String definition = definitions.get(0);
        List<String> missing = enumColumn.values().stream()
                .filter(value -> !definition.contains("'" + value + "'"))
                .toList();
        if (missing.isEmpty()) {
            return;
        }

        String allowed = enumColumn.values().stream()
                .map(value -> "'" + value + "'")
                .collect(Collectors.joining(", "));
        jdbcTemplate.execute("ALTER TABLE " + TABLE
                + " DROP CONSTRAINT " + enumColumn.constraintName()
                + ", ADD CONSTRAINT " + enumColumn.constraintName()
                + " CHECK (" + enumColumn.column() + " IN (" + allowed + "))");
        log.info("Widened check constraint {} on {} to accept {}", enumColumn.constraintName(), TABLE, missing);
    }
}
