package com.mercatto.integration;

import com.mercatto.orders.service.PaymentGateway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Checkout against a declining payment provider. Kept in its own class because replacing the
 * {@link PaymentGateway} port with a mock gives this class its own Spring application context
 * (same Postgres container), so the mock never leaks into {@link CheckoutFlowIntegrationTest}.
 */
class DeclinedPaymentCheckoutIntegrationTest extends PostgresIntegrationTest {

    @MockBean
    private PaymentGateway paymentGateway;

    @Test
    void declinedPaymentMarksTheOrderFailedAndLeavesStockUntouched() {
        when(paymentGateway.charge(anyLong(), any(), anyString()))
                .thenReturn(new PaymentGateway.PaymentResult(false, null, "Card declined"));
        TestUser seller = seller();
        TestUser buyer = buyer();
        Long product = createProduct(seller, "30.00", 5);

        ResponseEntity<Map<String, Object>> response = checkout(buyer, List.of(item(product, 2)), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("FAILED");
        // No OrderPlacedEvent is published for a declined charge, so Catalog is never touched.
        assertThat(stockOf(product)).isEqualTo(5);
        assertThat(listOrders(buyer)).singleElement()
                .satisfies(order -> assertThat(order.get("status")).isEqualTo("FAILED"));
    }

    @Test
    void retryingADeclinedOrderWithTheSameIdempotencyKeyChargesItAndThenDecrementsStock() {
        when(paymentGateway.charge(anyLong(), any(), anyString()))
                .thenReturn(new PaymentGateway.PaymentResult(false, null, "Card declined"))
                .thenReturn(new PaymentGateway.PaymentResult(true, "tx_" + UUID.randomUUID(), "Approved"));
        TestUser seller = seller();
        TestUser buyer = buyer();
        Long product = createProduct(seller, "30.00", 5);
        String key = UUID.randomUUID().toString();

        ResponseEntity<Map<String, Object>> declined = checkout(buyer, List.of(item(product, 2)), key);
        assertThat(declined.getBody().get("status")).isEqualTo("FAILED");
        assertThat(stockOf(product)).isEqualTo(5);

        ResponseEntity<Map<String, Object>> retried = checkout(buyer, List.of(item(product, 2)), key);

        assertThat(retried.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(retried.getBody().get("status")).isEqualTo("PAID");
        assertThat(asLong(retried.getBody().get("id"))).isEqualTo(asLong(declined.getBody().get("id")));
        // Synchronous AFTER_COMMIT listener: already applied when the response returned.
        assertThat(stockOf(product)).isEqualTo(3);
        assertThat(listOrders(buyer)).hasSize(1);
    }
}
