package com.mercatto.integration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Frequently bought together (#224) against real PostgreSQL, over the #219 recommendation fixture
 * ({@code search-eval/recommendation-dataset.json}) loaded through the public HTTP APIs by
 * {@link RecommendationDatasetLoader}. Every product the fixture names is created fresh by this
 * class (in its own random category, with plenty of stock), so the planted co-purchase clusters
 * aren't disturbed by orders placed by other tests.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FrequentlyBoughtTogetherIntegrationTest extends PostgresIntegrationTest {

    private static final String PS5 = "PlayStation 5 Console (PS5)";
    private static final String DUALSENSE = "PlayStation DualSense Wireless Controller – Midnight Black";
    private static final String CORSAIR_PSU = "Corsair RM750e (2023) Fully Modular Low-Noise Power Supply - ATX 3.0 "
            + "& PCIe 5.0 Compliant - 105°C-Rated Capacitors - 80 Plus Gold Efficiency - Modern Standby Support - Black";

    private SearchEvalFixtures.Dataset dataset;
    private RecommendationDatasetLoader.LoadedDataset loaded;
    private TestUser seller;
    private String category;

    @BeforeAll
    void loadFixture() {
        dataset = SearchEvalFixtures.dataset();
        seller = seller();
        category = "IT bought together " + UUID.randomUUID();

        Set<String> names = new LinkedHashSet<>();
        dataset.orders().forEach(order -> order.items().forEach(item -> names.add(item.product())));
        dataset.reviews().forEach(review -> names.add(review.product()));
        dataset.lists().forEach(list -> names.addAll(list.products()));
        dataset.expectations().alsoBought().forEach(alsoBought -> {
            names.add(alsoBought.product());
            names.addAll(alsoBought.expected());
        });

        Map<String, Long> productIds = new LinkedHashMap<>();
        for (String name : names) {
            productIds.put(name, createNamedProduct(name, 1000));
        }
        loaded = new RecommendationDatasetLoader(this).load(dataset, productIds);
        assertThat(loaded.orderIds()).hasSize(dataset.orders().size());
    }

    @Test
    void everyPlantedPairIsReturnedAsBoughtTogether() {
        for (SearchEvalFixtures.AlsoBought expectation : dataset.expectations().alsoBought()) {
            Map<String, Object> bundle = boughtTogether(loaded.productId(expectation.product()), 2);

            assertThat(bundle.get("source")).as(expectation.product()).isEqualTo("CO_PURCHASE");
            assertThat(itemIds(bundle)).as(expectation.product())
                    .containsExactlyInAnyOrderElementsOf(expectation.expected().stream().map(loaded::productId).toList());
            assertThat(items(bundle)).as(expectation.product())
                    .allSatisfy(item -> assertThat(((Number) item.get("timesBoughtTogether")).intValue())
                            .isGreaterThanOrEqualTo(2));
        }
    }

    @Test
    void theStrongerPairComesFirst() {
        // PS5 + DualSense: 3 buyers; PS5 + Spider-Man 2: 2 buyers (same popularity normalization).
        Map<String, Object> bundle = boughtTogether(loaded.productId(PS5), 2);

        assertThat(itemIds(bundle).get(0)).isEqualTo(loaded.productId(DUALSENSE));
        assertThat(((Number) items(bundle).get(0).get("timesBoughtTogether")).intValue()).isEqualTo(3);
    }

    @Test
    void aProductBoughtWithOthersByASingleBuyerFallsBackToSimilarProducts() {
        // The PSU was bought once, with the GPU, CPU and RAM: support 1, below the minimum.
        Map<String, Object> bundle = boughtTogether(loaded.productId(CORSAIR_PSU), 2);

        assertThat(bundle.get("source")).isEqualTo("SIMILAR");
        assertThat(items(bundle)).allSatisfy(item -> {
            assertThat(item.get("timesBoughtTogether")).isNull();
            assertThat(item.get("primaryReason")).isNotNull();
        });
    }

    @Test
    void aProductNeverBoughtFallsBackToSimilarProducts() {
        Long fresh = createNamedProduct("IT never bought " + UUID.randomUUID(), 5);

        Map<String, Object> bundle = boughtTogether(fresh, 2);

        assertThat(bundle.get("source")).isEqualTo("SIMILAR");
        assertThat(itemIds(bundle)).doesNotContain(fresh);
    }

    @Test
    void anOutOfStockPartnerIsDropped() {
        // A pair bought by two buyers, then the partner sells out.
        Long product = createNamedProduct("IT anchor " + UUID.randomUUID(), 10);
        Long partner = createNamedProduct("IT partner " + UUID.randomUUID(), 2);
        for (int i = 0; i < 2; i++) {
            ResponseEntity<Map<String, Object>> response =
                    checkout(buyer(), List.of(item(product, 1), item(partner, 1)), null);
            assertThat(response.getBody().get("status")).isEqualTo("PAID");
        }
        assertThat(stockOf(partner)).isZero();

        Map<String, Object> bundle = boughtTogether(product, 2);

        assertThat(bundle.get("source")).isEqualTo("SIMILAR");
        assertThat(itemIds(bundle)).doesNotContain(partner);
    }

    @Test
    void isPublicAndReturns404ForAnUnknownProduct() {
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                "/api/orders/bought-together/" + Long.MAX_VALUE, HttpMethod.GET, HttpEntity.EMPTY, MAP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsAnOutOfRangeLimit() {
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                "/api/orders/bought-together/" + loaded.productId(PS5) + "?limit=11", HttpMethod.GET,
                HttpEntity.EMPTY, MAP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private Long createNamedProduct(String name, int stock) {
        Map<String, Object> body = Map.of(
                "name", name,
                "price", new BigDecimal("49.90"),
                "stockQuantity", stock,
                "category", category);
        ResponseEntity<Map<String, Object>> created = rest.exchange("/api/catalog/products", HttpMethod.POST,
                json(body, seller.token()), MAP);
        assertThat(created.getStatusCode()).as(name).isEqualTo(HttpStatus.OK);
        return asLong(created.getBody().get("id"));
    }

    /** Anonymous GET: the endpoint is public, so no token is sent. */
    private Map<String, Object> boughtTogether(Long productId, int limit) {
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                "/api/orders/bought-together/" + productId + "?limit=" + limit, HttpMethod.GET, HttpEntity.EMPTY, MAP);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> bundle) {
        return (List<Map<String, Object>>) bundle.get("items");
    }

    @SuppressWarnings("unchecked")
    private static List<Long> itemIds(Map<String, Object> bundle) {
        return items(bundle).stream()
                .map(item -> asLong(((Map<String, Object>) item.get("product")).get("id")))
                .toList();
    }
}
