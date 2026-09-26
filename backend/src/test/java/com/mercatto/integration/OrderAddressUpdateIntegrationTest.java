package com.mercatto.integration;

import com.mercatto.orders.service.FulfillmentStatus;
import com.mercatto.orders.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code PATCH /api/orders/{id}/address} end to end against a real PostgreSQL (#173): the
 * {@code PESSIMISTIC_WRITE} lookup, and the order's lazy items being initialized before the
 * response is mapped outside the transaction (open-in-view is off).
 * <p>
 * The shared {@link TestRestTemplate} uses the JDK {@code HttpURLConnection}, which cannot send
 * PATCH, so the PATCH calls go through a second template backed by the {@code java.net.http}
 * client. Shipping goes through Orders' public service API, as there is no HTTP endpoint for it
 * yet (#178).
 */
class OrderAddressUpdateIntegrationTest extends PostgresIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    private static final Map<String, Object> NEW_ADDRESS = Map.of(
            "fullName", "Grace Hopper",
            "street", "200 Navy Way",
            "city", "Arlington",
            "state", "VA",
            "zip", "22202");

    @Autowired
    private OrderService orderService;

    private TestRestTemplate patchClient;

    @BeforeEach
    void setUpPatchClient() {
        patchClient = new TestRestTemplate(new RestTemplateBuilder()
                .rootUri(rest.getRootUri())
                .requestFactory(JdkClientHttpRequestFactory.class));
    }

    private ResponseEntity<Map<String, Object>> patchAddress(TestUser user, Long orderId, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(user.token());
        return patchClient.exchange("/api/orders/" + orderId + "/address", HttpMethod.PATCH,
                new HttpEntity<>(body, headers), MAP);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> addressOf(TestUser buyer, Long orderId) {
        return (Map<String, Object>) getOrder(buyer, orderId).getBody().get("address");
    }

    private Long placeOrder(TestUser seller, TestUser buyer) {
        Long product = createProduct(seller, "10.00", 5);
        ResponseEntity<Map<String, Object>> placed = checkout(buyer, List.of(item(product, 2)), null);
        assertThat(placed.getStatusCode()).isEqualTo(HttpStatus.OK);
        return asLong(placed.getBody().get("id"));
    }

    @Test
    void buyerUpdatesTheAddressOfANotShippedOrderAndItIsPersisted() {
        TestUser seller = seller();
        TestUser buyer = buyer();
        Long orderId = placeOrder(seller, buyer);

        ResponseEntity<Map<String, Object>> updated = patchAddress(buyer, orderId, NEW_ADDRESS);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().get("id")).isEqualTo(orderId.intValue());
        // Items are lazily loaded under the row lock; mapping them must not fail after commit.
        assertThat((List<?>) updated.getBody().get("items")).hasSize(1);
        assertThat(updated.getBody().get("address")).isEqualTo(NEW_ADDRESS);

        assertThat(addressOf(buyer, orderId)).isEqualTo(NEW_ADDRESS);
    }

    @Test
    void anotherBuyerCannotUpdateTheAddress() {
        TestUser seller = seller();
        TestUser buyer = buyer();
        TestUser otherBuyer = buyer();
        Long orderId = placeOrder(seller, buyer);

        ResponseEntity<Map<String, Object>> response = patchAddress(otherBuyer, orderId, NEW_ADDRESS);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(addressOf(buyer, orderId)).containsEntry("fullName", "Integration Buyer");
    }

    @Test
    void addressCannotBeUpdatedOnceTheSellerShipsTheOrder() {
        TestUser seller = seller();
        TestUser buyer = buyer();
        Long orderId = placeOrder(seller, buyer);
        orderService.advanceFulfillment(orderId, seller.id(), FulfillmentStatus.SHIPPED);

        ResponseEntity<Map<String, Object>> response = patchAddress(buyer, orderId, NEW_ADDRESS);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("message")).isEqualTo("Order already shipped");
        assertThat(addressOf(buyer, orderId)).containsEntry("fullName", "Integration Buyer");
    }

    @Test
    void invalidPayloadIsRejectedAndTheAddressIsKept() {
        TestUser seller = seller();
        TestUser buyer = buyer();
        Long orderId = placeOrder(seller, buyer);

        ResponseEntity<Map<String, Object>> response =
                patchAddress(buyer, orderId, Map.of("fullName", "", "street", "x", "city", "y", "state", "z", "zip", "1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(addressOf(buyer, orderId)).containsEntry("fullName", "Integration Buyer");
    }

    @Test
    void missingOrderReturns404() {
        TestUser buyer = buyer();

        ResponseEntity<Map<String, Object>> response = patchAddress(buyer, Long.MAX_VALUE, NEW_ADDRESS);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
