package com.mercatto.orders.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderEnumCheckConstraintSyncTest {

    private static final String QUERY =
            "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid = to_regclass(?) AND conname = ?";

    private JdbcTemplate jdbcTemplate;
    private OrderEnumCheckConstraintSync sync;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        sync = new OrderEnumCheckConstraintSync(jdbcTemplate);
        // By default every constraint already accepts all of its enum's values.
        for (OrderEnumCheckConstraintSync.EnumColumn column : OrderEnumCheckConstraintSync.ENUM_COLUMNS) {
            stubDefinition(column.constraintName(), checkOf(column.column(), column.values()));
        }
    }

    @Test
    void widensStatusConstraintCreatedBeforeProcessingExisted() {
        stubDefinition("orders_status_check",
                checkOf("status", List.of("PENDING", "PAID", "FAILED", "CANCELLED")));

        sync.syncEnumCheckConstraints();

        verify(jdbcTemplate).execute("ALTER TABLE orders.orders DROP CONSTRAINT orders_status_check, "
                + "ADD CONSTRAINT orders_status_check "
                + "CHECK (status IN ('PENDING', 'PROCESSING', 'PAID', 'FAILED', 'CANCELLED'))");
    }

    @Test
    void leavesUpToDateConstraintsAlone() {
        sync.syncEnumCheckConstraints();

        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    void leavesMissingConstraintAlone() {
        stubDefinition("orders_status_check", null);

        sync.syncEnumCheckConstraints();

        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    void oneFailingConstraintDoesNotStopTheOthers() {
        when(jdbcTemplate.queryForList(eq(QUERY), eq(String.class), any(), eq("orders_status_check")))
                .thenThrow(new RuntimeException("boom"));
        stubDefinition("orders_payment_method_check", checkOf("payment_method", List.of("CARD")));

        sync.syncEnumCheckConstraints();

        verify(jdbcTemplate).execute("ALTER TABLE orders.orders DROP CONSTRAINT orders_payment_method_check, "
                + "ADD CONSTRAINT orders_payment_method_check "
                + "CHECK (payment_method IN ('CARD', 'STORE', 'GIFT'))");
    }

    private void stubDefinition(String constraintName, String definition) {
        when(jdbcTemplate.queryForList(eq(QUERY), eq(String.class), any(), eq(constraintName)))
                .thenReturn(definition == null ? List.of() : List.of(definition));
    }

    /** Mirrors how PostgreSQL renders the constraints Hibernate generates. */
    private static String checkOf(String column, List<String> values) {
        String array = values.stream()
                .map(value -> "'" + value + "'::character varying")
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        return "CHECK (((" + column + ")::text = ANY ((ARRAY[" + array + "])::text[])))";
    }
}
