package com.mercatto.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end checkout flow across modules against a real PostgreSQL (QA-4 / #64):
 * Orders places + charges the order, publishes {@code OrderPlacedEvent}, and
 * {@code OrderPlacedEventListener} decrements Catalog's stock after the checkout transaction
 * commits.
 *
 * <p>Stock is asserted <b>immediately</b> after the checkout HTTP response, with no polling:
 * the listener is a synchronous {@code @TransactionalEventListener(AFTER_COMMIT)} (no
 * {@code @Async}), so it runs on the request thread right after {@code checkout()}'s transaction
 * commits and before the controller returns. If it is ever made asynchronous, these assertions
 * need Awaitility-style polling.
 */
class CheckoutFlowIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void eachModuleOwnsItsOwnPostgresSchema() {
        List<String> schemas = jdbcTemplate.queryForList(
                "SELECT schema_name FROM information_schema.schemata", String.class);
        assertThat(schemas).contains("users", "catalog", "orders", "cart", "reviews", "lists");

        assertThat(tableSchema("products")).isEqualTo("catalog");
        assertThat(tableSchema("orders")).isEqualTo("orders");
        assertThat(tableSchema("order_items")).isEqualTo("orders");
        assertThat(tableSchema("users")).isEqualTo("users");
    }

    @Test
    void paidCheckoutDecrementsStockOfEveryProductAfterCommit() {
        TestUser seller = seller();
        TestUser buyer = buyer();
        Long keyboard = createProduct(seller, "19.90", 10);
        Long mouse = createProduct(seller, "5.25", 10);

        ResponseEntity<Map<String, Object>> response =
                checkout(buyer, List.of(item(keyboard, 3), item(mouse, 2)), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> order = response.getBody();
        assertThat(order.get("status")).isEqualTo("PAID");
        assertThat(asMoney(order.get("totalAmount"))).isEqualByComparingTo(new BigDecimal("70.20"));
        assertThat(asLong(order.get("buyerId"))).isEqualTo(buyer.id());
        assertThat(order.get("fulfillmentStatus")).isEqualTo("NOT_SHIPPED");
        assertThat(order.get("shippedAt")).isNull();
        assertThat(order.get("estimatedDeliveryDate")).isNotNull();
        assertThat(order).doesNotContainKey("idempotencyKey");

        // Synchronous AFTER_COMMIT listener: already applied by the time the response returned.
        assertThat(stockOf(keyboard)).isEqualTo(7);
        assertThat(stockOf(mouse)).isEqualTo(8);

        ResponseEntity<Map<String, Object>> fetched = getOrder(buyer, asLong(order.get("id")));
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().get("status")).isEqualTo("PAID");
        List<Map<String, Object>> items = items(fetched.getBody());
        assertThat(items).hasSize(2);
        assertThat(items).allSatisfy(line -> assertThat(asLong(line.get("sellerId"))).isEqualTo(seller.id()));
        assertThat(items).anySatisfy(line -> {
            assertThat(asLong(line.get("productId"))).isEqualTo(keyboard);
            assertThat(line.get("quantity")).isEqualTo(3);
            assertThat(asMoney(line.get("unitPrice"))).isEqualByComparingTo("19.90");
        });
        assertThat(items).anySatisfy(line -> {
            assertThat(asLong(line.get("productId"))).isEqualTo(mouse);
            assertThat(line.get("quantity")).isEqualTo(2);
            assertThat(asMoney(line.get("unitPrice"))).isEqualByComparingTo("5.25");
        });
    }

    /** Regression for #28: an order must never become PAID without enough stock. */
    @Test
    void insufficientStockIsRejectedWithoutCreatingOrChargingAnOrder() {
        TestUser seller = seller();
        TestUser buyer = buyer();
        Long product = createProduct(seller, "10.00", 2);

        ResponseEntity<Map<String, Object>> response = checkout(buyer, List.of(item(product, 3)), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stockOf(product)).isEqualTo(2);
        assertThat(listOrders(buyer)).isEmpty();
    }

    /** #28 variant: the stock check sums every line for the same product, not each line alone. */
    @Test
    void insufficientStockAcrossRepeatedLinesOfTheSameProductIsRejected() {
        TestUser seller = seller();
        TestUser buyer = buyer();
        Long product = createProduct(seller, "10.00", 2);

        ResponseEntity<Map<String, Object>> response =
                checkout(buyer, List.of(item(product, 2), item(product, 1)), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stockOf(product)).isEqualTo(2);
        assertThat(listOrders(buyer)).isEmpty();
    }

    @Test
    void buyingExactlyTheRemainingStockEmptiesItAndTheNextBuyerIsRejected() {
        TestUser seller = seller();
        Long product = createProduct(seller, "42.00", 3);

        TestUser firstBuyer = buyer();
        ResponseEntity<Map<String, Object>> first = checkout(firstBuyer, List.of(item(product, 3)), null);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody().get("status")).isEqualTo("PAID");
        assertThat(stockOf(product)).isZero();

        TestUser secondBuyer = buyer();
        ResponseEntity<Map<String, Object>> second = checkout(secondBuyer, List.of(item(product, 1)), null);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stockOf(product)).isZero();
        assertThat(listOrders(secondBuyer)).isEmpty();
    }

    @Test
    void replayingTheSameIdempotencyKeyReturnsTheSameOrderAndDecrementsStockOnce() {
        TestUser seller = seller();
        TestUser buyer = buyer();
        Long product = createProduct(seller, "15.00", 10);
        String key = UUID.randomUUID().toString();

        ResponseEntity<Map<String, Object>> first = checkout(buyer, List.of(item(product, 4)), key);
        ResponseEntity<Map<String, Object>> replay = checkout(buyer, List.of(item(product, 4)), key);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody().get("status")).isEqualTo("PAID");
        assertThat(replay.getBody().get("status")).isEqualTo("PAID");
        assertThat(asLong(replay.getBody().get("id"))).isEqualTo(asLong(first.getBody().get("id")));
        assertThat(stockOf(product)).isEqualTo(6);
        assertThat(listOrders(buyer)).hasSize(1);
    }

    /**
     * Two buyers race for the last unit. The stock pre-check in {@code OrderServiceImpl.checkout}
     * reads a snapshot, so both can pass it and both be charged — that residual race is
     * explicitly accepted for ORD-1 (see that method's Javadoc). What must hold regardless is that
     * at least one buyer gets the unit, every response is either PAID or a 409, and stock ends at
     * exactly 0: the decrement in {@code ProductService.decreaseStock} refuses to go negative.
     */
    @Test
    void concurrentCheckoutsOfTheLastUnitNeverDriveStockNegative() throws Exception {
        TestUser seller = seller();
        Long product = createProduct(seller, "99.00", 1);
        List<TestUser> buyers = List.of(buyer(), buyer());

        List<ResponseEntity<Map<String, Object>>> responses =
                runConcurrently(buyers, buyer -> checkout(buyer, List.of(item(product, 1)), null));

        assertThat(responses).allSatisfy(response -> {
            if (response.getStatusCode() == HttpStatus.OK) {
                assertThat(response.getBody().get("status")).isEqualTo("PAID");
            } else {
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            }
        });
        assertThat(responses).anySatisfy(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK));
        assertThat(stockOf(product)).isZero();
    }

    /**
     * Concurrent stock decrements for the same product must all be applied (no lost update):
     * {@code Product}'s {@code @Version} turns a concurrent write into an optimistic-lock failure
     * that {@code OrderPlacedEventListener} retries in a fresh transaction. Kept at 3 buyers to
     * stay within the listener's MAX_ATTEMPTS = 3 (the worst case is losing to each other buyer
     * once).
     */
    @Test
    void concurrentCheckoutsWithEnoughStockAreAllAppliedWithoutLostUpdates() throws Exception {
        TestUser seller = seller();
        Long product = createProduct(seller, "7.50", 10);
        List<TestUser> buyers = List.of(buyer(), buyer(), buyer());

        List<ResponseEntity<Map<String, Object>>> responses =
                runConcurrently(buyers, buyer -> checkout(buyer, List.of(item(product, 2)), null));

        assertThat(responses).allSatisfy(response -> {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().get("status")).isEqualTo("PAID");
        });
        assertThat(stockOf(product)).isEqualTo(4);
    }

    private interface BuyerAction {
        ResponseEntity<Map<String, Object>> run(TestUser buyer);
    }

    /** Releases every buyer's request at the same instant and collects all responses. */
    private static List<ResponseEntity<Map<String, Object>>> runConcurrently(List<TestUser> buyers, BuyerAction action)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(buyers.size());
        CountDownLatch ready = new CountDownLatch(buyers.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ResponseEntity<Map<String, Object>>>> futures = new ArrayList<>();
            for (TestUser buyer : buyers) {
                Callable<ResponseEntity<Map<String, Object>>> task = () -> {
                    ready.countDown();
                    start.await();
                    return action.run(buyer);
                };
                futures.add(pool.submit(task));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<ResponseEntity<Map<String, Object>>> responses = new ArrayList<>();
            for (Future<ResponseEntity<Map<String, Object>>> future : futures) {
                responses.add(future.get(30, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            pool.shutdownNow();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> order) {
        return (List<Map<String, Object>>) order.get("items");
    }

    private String tableSchema(String table) {
        return jdbcTemplate.queryForObject(
                "SELECT table_schema FROM information_schema.tables WHERE table_name = ? AND table_schema <> 'public'",
                String.class, table);
    }
}
