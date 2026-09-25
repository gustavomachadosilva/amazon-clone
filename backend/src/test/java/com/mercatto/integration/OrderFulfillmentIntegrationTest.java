package com.mercatto.integration;

import com.mercatto.orders.service.FulfillmentStatus;
import com.mercatto.orders.service.InvalidFulfillmentTransitionException;
import com.mercatto.orders.service.OrderAccessDeniedException;
import com.mercatto.orders.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link OrderService#advanceFulfillment} against a real PostgreSQL (#172): exercises the
 * {@code PESSIMISTIC_WRITE} lookup (SELECT ... FOR UPDATE, which Postgres rejects on the nullable
 * side of an outer join) and the lazy load of the order's items inside that transaction.
 * There is no HTTP endpoint for this yet (#178), so it is called through Orders' public service
 * API; the order itself is placed and read back over HTTP.
 */
class OrderFulfillmentIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Test
    void sellerAdvancesAPaidOrderStepByStepAndBuyerSeesTheTimestamps() {
        TestUser seller = seller();
        TestUser buyer = buyer();
        Long product = createProduct(seller, "12.00", 5);
        ResponseEntity<Map<String, Object>> placed = checkout(buyer, List.of(item(product, 1)), null);
        assertThat(placed.getStatusCode()).isEqualTo(HttpStatus.OK);
        Long orderId = asLong(placed.getBody().get("id"));

        OrderService.OrderView shipped = orderService.advanceFulfillment(orderId, seller.id(), FulfillmentStatus.SHIPPED);
        assertThat(shipped.fulfillmentStatus()).isEqualTo(FulfillmentStatus.SHIPPED);
        assertThat(shipped.items()).hasSize(1);

        Map<String, Object> afterShipping = getOrder(buyer, orderId).getBody();
        assertThat(afterShipping.get("fulfillmentStatus")).isEqualTo("SHIPPED");
        assertThat(afterShipping.get("shippedAt")).isNotNull();
        assertThat(afterShipping.get("outForDeliveryAt")).isNull();

        assertThatThrownBy(() -> orderService.advanceFulfillment(orderId, seller.id(), FulfillmentStatus.SHIPPED))
                .isInstanceOf(InvalidFulfillmentTransitionException.class);

        orderService.advanceFulfillment(orderId, seller.id(), FulfillmentStatus.OUT_FOR_DELIVERY);
        orderService.advanceFulfillment(orderId, seller.id(), FulfillmentStatus.DELIVERED);

        Map<String, Object> delivered = getOrder(buyer, orderId).getBody();
        assertThat(delivered.get("fulfillmentStatus")).isEqualTo("DELIVERED");
        assertThat(delivered.get("outForDeliveryAt")).isNotNull();
        assertThat(delivered.get("deliveredAt")).isNotNull();
    }

    @Test
    void sellerWithoutItemsInTheOrderCannotAdvanceIt() {
        TestUser seller = seller();
        TestUser otherSeller = seller();
        TestUser buyer = buyer();
        Long product = createProduct(seller, "8.00", 5);
        Long orderId = asLong(checkout(buyer, List.of(item(product, 1)), null).getBody().get("id"));

        assertThatThrownBy(() -> orderService.advanceFulfillment(orderId, otherSeller.id(), FulfillmentStatus.SHIPPED))
                .isInstanceOf(OrderAccessDeniedException.class);
        assertThat(getOrder(buyer, orderId).getBody().get("fulfillmentStatus")).isEqualTo("NOT_SHIPPED");
    }
}
