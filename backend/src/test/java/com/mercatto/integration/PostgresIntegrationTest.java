package com.mercatto.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base for integration tests that boot the whole application (every module, the real
 * {@code JwtAuthenticationFilter}, Hibernate {@code ddl-auto: update}) against a real PostgreSQL
 * started by Testcontainers — per-module schemas and {@code AFTER_COMMIT} listeners don't behave
 * the same on an in-memory database (QA-4 / #64). Requires a running Docker daemon; without one
 * the subclasses are skipped instead of failing ({@code disabledWithoutDocker}).
 *
 * <p>Deliberately <b>not</b> {@code @Transactional}: a test-managed transaction would never
 * commit, so {@code @TransactionalEventListener(AFTER_COMMIT)} listeners (e.g. the stock
 * decrement after checkout) would never fire. Every test instead creates its own uniquely named
 * users/products, so tests never depend on each other's data or on execution order.
 *
 * <p>Tests talk to the app only over HTTP with plain {@link Map}s, never through another
 * module's {@code domain}/{@code repository} classes, so they stay within the Contrato de
 * Modularidade that {@code ArchitectureBoundaryTest} enforces (it also scans test classes).
 *
 * <p>{@code stripe.api-key} is forced blank so {@code PaymentGatewayConfig} always wires the
 * in-memory {@code MockPaymentGateway}, even if {@code STRIPE_API_KEY} is set in the shell;
 * likewise {@code payment.mock.decline} is forced to {@code none} so a {@code PAYMENT_MOCK_DECLINE}
 * in the shell never makes that mock decline.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "stripe.api-key=",
                "payment.mock.decline=none",
                "jwt.secret=integration-test-secret",
                "logging.level.com.mercatto=INFO"
        })
@Testcontainers(disabledWithoutDocker = true)
abstract class PostgresIntegrationTest {

    /**
     * One container for the whole test JVM (singleton pattern), started on first use and removed
     * by Testcontainers' Ryuk reaper at JVM exit. Not a JUnit {@code @Container}: that would stop
     * it after each test class while Spring's cached application context still points at it.
     * The init script is the same one docker-compose runs, creating one schema per module.
     */
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withInitScript("db/init/001-schemas.sql");

    static {
        POSTGRES.start();
    }

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_OF_MAPS =
            new ParameterizedTypeReference<>() {};

    @Autowired
    protected TestRestTemplate rest;

    /** A registered + logged-in user: its id and a bearer token for {@code Authorization}. */
    protected record TestUser(Long id, String token) {}

    protected TestUser registerAndLogin(String role) {
        String email = role.toLowerCase() + "-" + UUID.randomUUID() + "@it.mercatto.dev";
        String password = "Password123!";

        ResponseEntity<Map<String, Object>> registered = rest.exchange("/api/users/register", HttpMethod.POST,
                json(Map.of("name", "IT " + role, "email", email, "password", password, "role", role), null),
                MAP);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map<String, Object>> login = rest.exchange("/api/users/login", HttpMethod.POST,
                json(Map.of("email", email, "password", password), null), MAP);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return new TestUser(asLong(login.getBody().get("id")), (String) login.getBody().get("token"));
    }

    protected TestUser buyer() {
        return registerAndLogin("BUYER");
    }

    protected TestUser seller() {
        return registerAndLogin("SELLER");
    }

    /** Creates a product owned by {@code seller} and returns its id. */
    protected Long createProduct(TestUser seller, String price, int stock) {
        Map<String, Object> body = Map.of(
                "name", "IT product " + UUID.randomUUID(),
                "price", new BigDecimal(price),
                "stockQuantity", stock,
                "category", "Integration Tests");
        ResponseEntity<Map<String, Object>> created = rest.exchange("/api/catalog/products", HttpMethod.POST,
                json(body, seller.token()), MAP);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        return asLong(created.getBody().get("id"));
    }

    /** One checkout line: {@code productId} x {@code quantity}. */
    protected static Map<String, Object> item(Long productId, int quantity) {
        return Map.of("productId", productId, "quantity", quantity);
    }

    /**
     * POSTs {@code /api/orders/checkout} for {@code buyer}. {@code idempotencyKey} is sent as the
     * {@code Idempotency-Key} header when non-null.
     */
    protected ResponseEntity<Map<String, Object>> checkout(TestUser buyer, List<Map<String, Object>> items,
                                                           String idempotencyKey) {
        Map<String, Object> body = Map.of(
                "items", items,
                "address", Map.of(
                        "fullName", "Integration Buyer",
                        "street", "Av. Bento Goncalves, 9500",
                        "city", "Porto Alegre",
                        "state", "RS",
                        "zip", "91501-970"),
                "shippingMethod", "STANDARD",
                "paymentMethod", "CARD");
        HttpHeaders headers = jsonHeaders(buyer.token());
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return rest.exchange("/api/orders/checkout", HttpMethod.POST, new HttpEntity<>(body, headers), MAP);
    }

    /** Current stock of a product, read through Catalog's public HTTP API. */
    protected int stockOf(Long productId) {
        ResponseEntity<Map<String, Object>> product = rest.exchange(
                "/api/catalog/products/" + productId, HttpMethod.GET, HttpEntity.EMPTY, MAP);
        assertThat(product.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((Number) product.getBody().get("stockQuantity")).intValue();
    }

    protected ResponseEntity<Map<String, Object>> getOrder(TestUser user, Long orderId) {
        return rest.exchange("/api/orders/" + orderId, HttpMethod.GET, json(null, user.token()), MAP);
    }

    protected List<Map<String, Object>> listOrders(TestUser user) {
        ResponseEntity<List<Map<String, Object>>> orders = rest.exchange(
                "/api/orders", HttpMethod.GET, json(null, user.token()), LIST_OF_MAPS);
        assertThat(orders.getStatusCode()).isEqualTo(HttpStatus.OK);
        return orders.getBody();
    }

    protected static Long asLong(Object number) {
        return ((Number) number).longValue();
    }

    /** JSON numbers deserialize into a {@link Map} as Integer/Double; compare money exactly. */
    protected static BigDecimal asMoney(Object number) {
        return new BigDecimal(number.toString());
    }

    private static <T> HttpEntity<T> json(T body, String bearerToken) {
        return new HttpEntity<>(body, jsonHeaders(bearerToken));
    }

    private static HttpHeaders jsonHeaders(String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        return headers;
    }
}
