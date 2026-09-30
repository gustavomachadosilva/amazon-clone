package com.mercatto.integration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Home's "Recommended for you" / "Top rated" shelf (#225) against real PostgreSQL, over the #219
 * recommendation fixture ({@code search-eval/recommendation-dataset.json}) loaded through the public
 * HTTP APIs by {@link RecommendationDatasetLoader}.
 *
 * <p>Like {@link FrequentlyBoughtTogetherIntegrationTest}, every product the fixture names is created
 * fresh by this class with plenty of stock — but here each one keeps its seed category (read from
 * {@link SeedCatalogCsv}), prefixed with a per-run tag, since the category is half of the signal.
 * Those categories exist only in this run, so the personalized part of each shelf is fully
 * determined by the fixture. The fallback part reads the whole (shared) database, so the anonymous
 * tests only assert its structure (in stock, category diversity), never exact items.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HomeRecommendationsIntegrationTest extends PostgresIntegrationTest {

    private static final int SHELF = 12;
    private static final Set<String> PERSONALIZED_REASONS = Set.of("BOUGHT_TOGETHER", "CATEGORY_AFFINITY");

    private static final String MORTAL_KOMBAT = "Mortal Kombat 1 - PlayStation5";
    private static final String LOGITECH_G502 = "Logitech G502 HERO High Performance Wired Gaming Mouse, HERO 25K "
            + "Sensor, 25,600 DPI, RGB, Adjustable Weights, 11 Programmable Buttons, On-Board Memory, PC / Mac, Black";

    private SearchEvalFixtures.Dataset dataset;
    private RecommendationDatasetLoader.LoadedDataset loaded;
    private TestUser seller;
    private String runTag;
    private final Map<String, String> categoryByName = new HashMap<>();

    @BeforeAll
    void loadFixture() {
        dataset = SearchEvalFixtures.dataset();
        seller = seller();
        runTag = UUID.randomUUID().toString().substring(0, 8);
        Map<String, String> seedCategories = new HashMap<>();
        SeedCatalogCsv.read().forEach(row -> seedCategories.putIfAbsent(row.name(), row.category()));

        Set<String> names = new LinkedHashSet<>();
        dataset.orders().forEach(order -> order.items().forEach(item -> names.add(item.product())));
        dataset.reviews().forEach(review -> names.add(review.product()));
        dataset.lists().forEach(list -> names.addAll(list.products()));
        dataset.expectations().forBuyer().forEach(forBuyer -> names.addAll(forBuyer.heldOut()));

        Map<String, Long> productIds = new LinkedHashMap<>();
        for (String name : names) {
            String seedCategory = seedCategories.get(name);
            assertThat(seedCategory).as("seed category of " + name).isNotNull();
            String category = categoryOf(seedCategory);
            categoryByName.put(name, category);
            productIds.put(name, createProduct(name, category, 1000));
        }
        loaded = new RecommendationDatasetLoader(this).load(dataset, productIds);
        assertThat(loaded.orderIds()).hasSize(dataset.orders().size());
    }

    @Test
    void everyHeldOutProductIsRecommendedToItsBuyer() {
        int expected = 0;
        int hits = 0;
        for (SearchEvalFixtures.ForBuyer expectation : dataset.expectations().forBuyer()) {
            Map<String, Object> shelf = home(loaded.buyers().get(expectation.buyer()).token(), SHELF);
            List<Long> heldOut = expectation.heldOut().stream().map(loaded::productId).toList();

            assertThat(shelf.get("layer")).as(expectation.buyer()).isEqualTo("PERSONALIZED");
            assertThat(itemIds(shelf)).as(expectation.buyer()).containsAll(heldOut);

            expected += heldOut.size();
            hits += (int) heldOut.stream().filter(itemIds(shelf)::contains).count();
            System.out.printf("[#225] %s: held-out at positions %s of %d%n", expectation.buyer(),
                    heldOut.stream().map(id -> itemIds(shelf).indexOf(id) + 1).toList(), SHELF);
        }
        // Reported in docs/search-recommendation-baseline.md ("Recommended for you (#225)").
        System.out.printf("[#225] forBuyer hit rate@%d = %d/%d%n", SHELF, hits, expected);
        assertThat(hits).isEqualTo(expected);
    }

    @Test
    void buyersWithDifferentHistoriesGetDifferentShelvesFromTheirOwnClusters() {
        Map<String, Object> gamer = home(loaded.buyers().get("gamer-3").token(), SHELF);
        Map<String, Object> beauty = home(loaded.buyers().get("beauty-2").token(), SHELF);

        assertThat(itemIds(gamer)).isNotEqualTo(itemIds(beauty));
        assertThat(personalizedCategories(gamer)).isNotEmpty()
                .isSubsetOf(categoriesOf("Video Games", "Headphones & Earbuds", "Computer Components"));
        assertThat(personalizedCategories(beauty)).isNotEmpty()
                .isSubsetOf(categoriesOf("Makeup", "Skin Care Products"));
    }

    @Test
    void neverRecommendsWhatABuyerBoughtListedOrHasInTheCart() {
        TestUser gamer1 = loaded.buyers().get("gamer-1");
        addToCart(gamer1, loaded.productId(LOGITECH_G502));

        for (String buyer : dataset.buyers()) {
            Set<Long> owned = new HashSet<>();
            dataset.orders().stream().filter(order -> order.buyer().equals(buyer))
                    .forEach(order -> order.items().forEach(item -> owned.add(loaded.productId(item.product()))));
            dataset.lists().stream().filter(list -> list.buyer().equals(buyer))
                    .forEach(list -> list.products().forEach(product -> owned.add(loaded.productId(product))));
            if (buyer.equals("gamer-1")) {
                owned.add(loaded.productId(LOGITECH_G502));
            }

            Map<String, Object> shelf = home(loaded.buyers().get(buyer).token(), SHELF);

            assertThat(itemIds(shelf)).as(buyer).doesNotContainAnyElementsOf(owned);
            assertThat(items(shelf)).as(buyer).allSatisfy(item ->
                    assertThat(((Number) product(item).get("stockQuantity")).intValue()).isPositive());
        }
    }

    @Test
    void theCartAloneIsASignal() {
        TestUser fresh = buyer();
        Long carted = loaded.productId(MORTAL_KOMBAT);
        addToCart(fresh, carted);

        Map<String, Object> shelf = home(fresh.token(), SHELF);

        assertThat(shelf.get("layer")).isEqualTo("PERSONALIZED");
        assertThat(itemIds(shelf)).doesNotContain(carted);
        List<Map<String, Object>> personalized = items(shelf).stream()
                .filter(item -> PERSONALIZED_REASONS.contains((String) item.get("reason")))
                .toList();
        assertThat(personalized).hasSizeGreaterThanOrEqualTo(3)
                .allSatisfy(item -> assertThat(product(item).get("category"))
                        .isEqualTo(categoryByName.get(MORTAL_KOMBAT)));
    }

    @Test
    void aSoldOutCoPurchasedPartnerIsNeverRecommended() {
        String category = "IT rec sold out " + UUID.randomUUID();
        Long anchor = createProduct("IT anchor " + UUID.randomUUID(), category, 10);
        Long soldOut = createProduct("IT partner " + UUID.randomUUID(), category, 2);
        Long inStock = createProduct("IT partner " + UUID.randomUUID(), category, 10);
        for (int i = 0; i < 3; i++) {
            createProduct("IT filler " + UUID.randomUUID(), category, 5);
        }
        for (int i = 0; i < 2; i++) {
            ResponseEntity<Map<String, Object>> response =
                    checkout(buyer(), List.of(item(anchor, 1), item(soldOut, 1), item(inStock, 1)), null);
            assertThat(response.getBody().get("status")).isEqualTo("PAID");
        }
        assertThat(stockOf(soldOut)).isZero();
        TestUser buyer = buyer();
        assertThat(checkout(buyer, List.of(item(anchor, 1)), null).getBody().get("status")).isEqualTo("PAID");

        Map<String, Object> shelf = home(buyer.token(), SHELF);

        assertThat(shelf.get("layer")).isEqualTo("PERSONALIZED");
        assertThat(itemIds(shelf)).doesNotContain(soldOut, anchor);
        assertThat(items(shelf)).anySatisfy(item -> {
            assertThat(asLong(product(item).get("id"))).isEqualTo(inStock);
            assertThat(item.get("reason")).isEqualTo("BOUGHT_TOGETHER");
        });
    }

    @Test
    void anonymousVisitorsGetTopRatedInStockProductsFromManyCategories() {
        // Enough in-stock categories of our own for a full shelf at two per category, whatever else
        // the shared database holds.
        for (int c = 0; c < 7; c++) {
            String category = "IT rec anonymous " + runTag + " " + c;
            for (int i = 0; i < 3; i++) {
                createProduct("IT anonymous " + UUID.randomUUID(), category, 5);
            }
        }

        ResponseEntity<Map<String, Object>> response = rest.exchange("/api/recommendations/home", HttpMethod.GET,
                HttpEntity.EMPTY, MAP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> shelf = response.getBody();
        assertThat(shelf.get("layer")).isEqualTo("TOP_RATED");
        assertThat(items(shelf)).hasSize(SHELF);
        assertThat(items(shelf)).allSatisfy(item -> {
            assertThat(item.get("reason")).isIn("TOP_RATED", "BEST_SELLER");
            assertThat(((Number) product(item).get("stockQuantity")).intValue()).isPositive();
        });
        Map<Object, Long> perCategory = items(shelf).stream()
                .collect(Collectors.groupingBy(item -> product(item).get("category"), Collectors.counting()));
        assertThat(perCategory.values()).allSatisfy(count -> assertThat(count).isLessThanOrEqualTo(2L));
    }

    @Test
    void aBuyerWithoutHistoryGetsTopRated() {
        Map<String, Object> shelf = home(buyer().token(), SHELF);

        assertThat(shelf.get("layer")).isEqualTo("TOP_RATED");
    }

    @Test
    void anInvalidTokenIsRejectedInsteadOfServedAnonymously() {
        ResponseEntity<Map<String, Object>> response = rest.exchange("/api/recommendations/home", HttpMethod.GET,
                json(null, "not-a-valid-token"), MAP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsAnOutOfRangeLimit() {
        for (int limit : new int[] {0, 25}) {
            ResponseEntity<Map<String, Object>> response = rest.exchange(
                    "/api/recommendations/home?limit=" + limit, HttpMethod.GET, HttpEntity.EMPTY, MAP);

            assertThat(response.getStatusCode()).as("limit " + limit).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    private String categoryOf(String seedCategory) {
        return "IT rec " + runTag + " " + seedCategory;
    }

    private Set<Object> categoriesOf(String... seedCategories) {
        return java.util.Arrays.stream(seedCategories).map(this::categoryOf).collect(Collectors.toSet());
    }

    private Long createProduct(String name, String category, int stock) {
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

    private void addToCart(TestUser buyer, Long productId) {
        ResponseEntity<Map<String, Object>> response = rest.exchange("/api/cart/" + buyer.id() + "/items",
                HttpMethod.POST, json(Map.of("productId", productId, "quantity", 1), buyer.token()), MAP);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private Map<String, Object> home(String token, int limit) {
        ResponseEntity<Map<String, Object>> response = rest.exchange("/api/recommendations/home?limit=" + limit,
                HttpMethod.GET, json(null, token), MAP);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private Set<Object> personalizedCategories(Map<String, Object> shelf) {
        return items(shelf).stream()
                .filter(item -> PERSONALIZED_REASONS.contains((String) item.get("reason")))
                .map(item -> product(item).get("category"))
                .collect(Collectors.toSet());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> shelf) {
        return (List<Map<String, Object>>) shelf.get("items");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> product(Map<String, Object> item) {
        return (Map<String, Object>) item.get("product");
    }

    private static List<Long> itemIds(Map<String, Object> shelf) {
        return items(shelf).stream().map(item -> asLong(product(item).get("id"))).toList();
    }
}
