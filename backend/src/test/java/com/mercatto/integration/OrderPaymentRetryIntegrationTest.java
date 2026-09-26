package com.mercatto.integration;

import com.mercatto.orders.service.PaymentGateway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code POST /api/orders/{id}/payment} end to end against a real PostgreSQL (#174): a FAILED
 * order is charged again and, once approved, becomes PAID and has its stock decremented exactly
 * once through the AFTER_COMMIT {@code OrderPlacedEvent} listener.
 * <p>
 * Declares the {@link PaymentGateway} {@code @MockBean} exactly like
 * {@link DeclinedPaymentCheckoutIntegrationTest}, so both classes share one cached Spring context.
 */
class OrderPaymentRetryIntegrationTest extends PostgresIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    @MockBean
    private PaymentGateway paymentGateway;

    private static PaymentGateway.PaymentResult declined() {
        return new PaymentGateway.PaymentResult(false, null, "Card declined");
    }

    private static PaymentGateway.PaymentResult approved() {
        return new PaymentGateway.PaymentResult(true, "tx_" + UUID.randomUUID(), "Approved");
    }

    private ResponseEntity<Map<String, Object>> retryPayment(TestUser user, Long orderId, String paymentMethod) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(user.token());
        return rest.exchange("/api/orders/" + orderId + "/payment", HttpMethod.POST,
                new HttpEntity<>(Map.of("paymentMethod", paymentMethod), headers), MAP);
    }

    /** Places a {@code quantity}-unit order that the (currently stubbed) gateway declines. */
    private Long placeFailedOrder(TestUser buyer, Long product, int quantity) {
        ResponseEntity<Map<String, Object>> placed = checkout(buyer, List.of(item(product, quantity)), null);
        assertThat(placed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(placed.getBody().get("status")).isEqualTo("FAILED");
        return asLong(placed.getBody().get("id"));
    }

    private String statusOf(TestUser buyer, Long orderId) {
        return (String) getOrder(buyer, orderId).getBody().get("status");
    }

    @Test
    void failedOrderRetriedWithApprovalBecomesPaidAndDecrementsStockOnce() {
        when(paymentGateway.charge(anyLong(), any(), anyString())).thenReturn(declined(), approved());
        TestUser seller = seller();
        TestUser buyer = buyer();
        Long product = createProduct(seller, "30.00", 5);
        Long orderId = placeFailedOrder(buyer, product, 2);
        assertThat(stockOf(product)).isEqualTo(5);

        ResponseEntity<Map<String, Object>> retried = retryPayment(buyer, orderId, "GIFT");

        assertThat(retried.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(retried.getBody().get("status")).isEqualTo("PAID");
        assertThat(retried.getBody().get("paymentMethod")).isEqualTo("GIFT");
        assertThat(asMoney(retried.getBody().get("totalAmount"))).isEqualByComparingTo("60.00");
        // Synchronous AFTER_COMMIT listener: already applied when the response returned.
        assertThat(stockOf(product)).isEqualTo(3);
        Map<String, Object> persisted = getOrder(buyer, orderId).getBody();
        assertThat(persisted.get("status")).isEqualTo("PAID");
        assertThat(persisted.get("paymentMethod")).isEqualTo("GIFT");

        // A second retry (e.g. a late double click) is rejected and charges nothing.
        ResponseEntity<Map<String, Object>> again = retryPayment(buyer, orderId, "GIFT");

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stockOf(product)).isEqualTo(3);
        verify(paymentGateway, times(2)).charge(anyLong(), any(), anyString());
        assertThat(listOrders(buyer)).hasSize(1);
    }

    @Test
    void retryDeclinedAgainKeepsTheOrderFailedAndRetryable() {
        when(paymentGateway.charge(anyLong(), any(), anyString())).thenReturn(declined(), declined(), approved());
        TestUser seller = seller();
        TestUser buyer = buyer();
        Long product = createProduct(seller, "30.00", 5);
        Long orderId = placeFailedOrder(buyer, product, 2);

        ResponseEntity<Map<String, Object>> declinedAgain = retryPayment(buyer, orderId, "CARD");

        assertThat(declinedAgain.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(declinedAgain.getBody().get("status")).isEqualTo("FAILED");
        assertThat(stockOf(product)).isEqualTo(5);

        ResponseEntity<Map<String, Object>> approvedRetry = retryPayment(buyer, orderId, "CARD");

        assertThat(approvedRetry.getBody().get("status")).isEqualTo("PAID");
        assertThat(stockOf(product)).isEqualTo(3);
    }

    @Test
    void retryOfAPaidOrderIsRejectedWithoutCharging() {
        when(paymentGateway.charge(anyLong(), any(), anyString())).thenReturn(approved());
        TestUser seller = seller();
        TestUser buyer = buyer();
        Long product = createProduct(seller, "30.00", 5);
        ResponseEntity<Map<String, Object>> placed = checkout(buyer, List.of(item(product, 2)), null);
        assertThat(placed.getBody().get("status")).isEqualTo("PAID");
        Long orderId = asLong(placed.getBody().get("id"));

        ResponseEntity<Map<String, Object>> response = retryPayment(buyer, orderId, "CARD");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stockOf(product)).isEqualTo(3);
        verify(paymentGateway, times(1)).charge(anyLong(), any(), anyString());
    }

    @Test
    void anotherBuyerGets403AndAnUnknownOrder404AndTheOrderStaysFailed() {
        when(paymentGateway.charge(anyLong(), any(), anyString())).thenReturn(declined(), approved());
        TestUser seller = seller();
        TestUser buyer = buyer();
        TestUser otherBuyer = buyer();
        Long product = createProduct(seller, "30.00", 5);
        Long orderId = placeFailedOrder(buyer, product, 2);

        assertThat(retryPayment(otherBuyer, orderId, "CARD").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(retryPayment(buyer, Long.MAX_VALUE, "CARD").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(statusOf(buyer, orderId)).isEqualTo("FAILED");
        assertThat(stockOf(product)).isEqualTo(5);
        verify(paymentGateway, times(1)).charge(anyLong(), any(), anyString());
    }

    @Test
    void retryIsRejectedWhenTheStockRanOutMeanwhile() {
        when(paymentGateway.charge(anyLong(), any(), anyString())).thenReturn(declined(), approved());
        TestUser seller = seller();
        TestUser buyer = buyer();
        TestUser otherBuyer = buyer();
        Long product = createProduct(seller, "30.00", 2);
        Long orderId = placeFailedOrder(buyer, product, 2);

        // Another buyer takes the last units while the first order sits FAILED.
        ResponseEntity<Map<String, Object>> otherOrder = checkout(otherBuyer, List.of(item(product, 2)), null);
        assertThat(otherOrder.getBody().get("status")).isEqualTo("PAID");
        assertThat(stockOf(product)).isZero();

        ResponseEntity<Map<String, Object>> response = retryPayment(buyer, orderId, "CARD");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(statusOf(buyer, orderId)).isEqualTo("FAILED");
        assertThat(stockOf(product)).isZero();
        verify(paymentGateway, times(2)).charge(anyLong(), any(), anyString());
    }

    @Test
    void doubleClickWhileTheFirstRetryIsBeingChargedIsRejectedAndChargesOnce() throws Exception {
        TestUser seller = seller();
        TestUser buyer = buyer();
        Long product = createProduct(seller, "30.00", 5);
        when(paymentGateway.charge(anyLong(), any(), anyString())).thenReturn(declined());
        Long orderId = placeFailedOrder(buyer, product, 2);

        // The retry's charge blocks until released, holding the order in PROCESSING.
        CountDownLatch charging = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(paymentGateway.charge(anyLong(), any(), anyString())).thenAnswer(invocation -> {
            charging.countDown();
            if (!release.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test never released the blocked charge");
            }
            return approved();
        });

        CompletableFuture<ResponseEntity<Map<String, Object>>> first =
                CompletableFuture.supplyAsync(() -> retryPayment(buyer, orderId, "CARD"));
        try {
            assertThat(charging.await(30, TimeUnit.SECONDS)).as("first retry reached the gateway").isTrue();

            ResponseEntity<Map<String, Object>> second = retryPayment(buyer, orderId, "CARD");

            assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        } finally {
            release.countDown();
        }

        ResponseEntity<Map<String, Object>> firstResponse = first.get(30, TimeUnit.SECONDS);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstResponse.getBody().get("status")).isEqualTo("PAID");
        assertThat(statusOf(buyer, orderId)).isEqualTo("PAID");
        assertThat(stockOf(product)).isEqualTo(3);
        // One declined checkout charge + exactly one retry charge.
        verify(paymentGateway, times(2)).charge(anyLong(), any(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void addressChangedWhileTheRetryIsBeingChargedIsKept() {
        TestUser seller = seller();
        TestUser buyer = buyer();
        Long product = createProduct(seller, "30.00", 5);
        when(paymentGateway.charge(anyLong(), any(), anyString())).thenReturn(declined());
        Long orderId = placeFailedOrder(buyer, product, 2);

        // While the retry is at the gateway, the buyer saves a new address through its own request
        // (on another thread, as the browser would send it). The shared template's HttpURLConnection
        // cannot send PATCH, hence the JDK-client-backed one (see OrderAddressUpdateIntegrationTest).
        TestRestTemplate patchClient = new TestRestTemplate(new RestTemplateBuilder()
                .rootUri(rest.getRootUri())
                .requestFactory(JdkClientHttpRequestFactory.class));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(buyer.token());
        Map<String, Object> newAddress = Map.of(
                "fullName", "Grace Hopper", "street", "200 Navy Way", "city", "Arlington", "state", "VA", "zip", "22202");
        when(paymentGateway.charge(anyLong(), any(), anyString())).thenAnswer(invocation -> {
            ResponseEntity<Map<String, Object>> patched = CompletableFuture.supplyAsync(() -> patchClient.exchange(
                    "/api/orders/" + orderId + "/address", HttpMethod.PATCH, new HttpEntity<>(newAddress, headers), MAP))
                    .get(30, TimeUnit.SECONDS);
            assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
            return approved();
        });

        ResponseEntity<Map<String, Object>> retried = retryPayment(buyer, orderId, "CARD");

        assertThat(retried.getBody().get("status")).isEqualTo("PAID");
        Map<String, Object> persisted = getOrder(buyer, orderId).getBody();
        assertThat(persisted.get("status")).isEqualTo("PAID");
        assertThat((Map<String, Object>) persisted.get("address")).containsEntry("fullName", "Grace Hopper");
        assertThat((Map<String, Object>) retried.getBody().get("address")).containsEntry("fullName", "Grace Hopper");
    }
}
