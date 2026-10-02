package com.mercatto.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The relevance order of the product search (#221), against the real PostgreSQL function
 * {@code catalog.product_relevance}: terms in the name head (before a "for …" compatibility tail)
 * above a brand match above terms elsewhere in the name above a category match above a
 * description match, a bonus for the terms as a contiguous phrase, no reward for repeating a word,
 * and {@code id ASC} between exact ties, so paging through a result set never repeats or skips a
 * product. Products are always created in the REVERSE of the expected order, so plain id order
 * would get every assertion backwards. The container is shared with the other integration tests,
 * so every test searches for its own random token ({@code zq} + random letters).
 */
class CatalogRelevanceIntegrationTest extends PostgresIntegrationTest {

    private static final int PAGE_SIZE = 5;

    @Test
    void nameHeadBeatsACompatibilityTail() {
        TestUser seller = seller();
        String tok = token();
        Long accessory = create(seller, product("Case for " + tok + " Stand"));
        Long compatible = create(seller, product("Charger Compatible with " + tok + " Stand"));
        Long theThing = create(seller, product(tok + " Stand"));

        List<Long> order = ids(search("query", tok + " stand"));

        assertThat(order.get(0)).isEqualTo(theThing);
        assertThat(order).containsExactlyInAnyOrder(theThing, accessory, compatible);
    }

    @Test
    void brandBeatsNameTailBeatsCategoryBeatsDescription() {
        TestUser seller = seller();
        String tok = token();
        Long inDescription = create(seller, product("Plain gadget").description("Pairs with the " + tok + " range"));
        Long inCategory = create(seller, product("Plain gadget").category("IT-" + tok));
        Long inNameTail = create(seller, product("Plain cover for " + tok));
        Long inBrand = create(seller, product("Plain gadget").brand(tok.toUpperCase()));

        assertThat(ids(search("query", tok))).containsExactly(inBrand, inNameTail, inCategory, inDescription);
    }

    @Test
    void termsAsAContiguousPhraseRankFirst() {
        TestUser seller = seller();
        String tok = token();
        Long scattered = create(seller, product("Alpha gamma beta " + tok));
        // Longer name: without the phrase bonus the normalized rank alone would put it second.
        Long phrase = create(seller, product("Alpha beta " + tok + " with a much longer name than the other one"));

        assertThat(ids(search("query", "alpha beta " + tok))).containsExactly(phrase, scattered);
    }

    @Test
    void repeatingTheTermNoLongerWins() {
        TestUser seller = seller();
        String tok = token();
        Long repeated = create(seller, product("Power cord for " + tok + " 5, " + tok + " 4, " + tok
                + " 3 slim and super slim consoles, replacement cable"));
        Long once = create(seller, product(tok + " 5 console"));

        assertThat(ids(search("query", tok))).containsExactly(once, repeated);
    }

    @Test
    void exactTiesAreOrderedByAscendingId() {
        TestUser seller = seller();
        String tok = token();
        Long first = create(seller, product(tok + " widget"));
        Long second = create(seller, product(tok + " widget"));
        Long third = create(seller, product(tok + " widget"));
        Long branded = create(seller, product(tok + " widget").brand(tok));

        assertThat(ids(search("query", tok))).containsExactly(branded, first, second, third);
        assertThat(ids(search("query", tok, "sort", "relevance"))).containsExactly(branded, first, second, third);
    }

    @Test
    void pagingThroughAResultSetNeverRepeatsNorSkipsAProduct() {
        Fixture fixture = paginationFixture();

        for (List<String> params : fixture.orderings()) {
            List<Long> paged = readAllPages(params, null);
            assertCompleteAndStable(fixture, params, paged);
        }
    }

    @Test
    void pagingStaysCompleteWhenAProductIsUpdatedBetweenPages() {
        Fixture fixture = paginationFixture();

        for (List<String> params : fixture.orderings()) {
            List<Long> paged = readAllPages(params, fixture);
            assertCompleteAndStable(fixture, params, paged);
        }
    }

    // --- pagination fixture --------------------------------------------------------------------

    /**
     * 23 products in their own category, all matching the fixture token: several relevance tiers
     * (name head, compatibility tail, brand, description only) and many exact ties (identical
     * names, brands and prices), so only the id tie-break separates them.
     */
    private Fixture paginationFixture() {
        TestUser seller = seller();
        String tok = token();
        String category = "IT-" + UUID.randomUUID();
        Map<Long, ProductBody> products = new LinkedHashMap<>();
        String[] prices = {"10.00", "20.00", "20.00", "30.00"};
        for (int i = 0; i < 23; i++) {
            ProductBody body = switch (i % 4) {
                case 0 -> product(tok + " widget");
                case 1 -> product("Case for " + tok + " widget");
                case 2 -> product("Plain gadget").brand(tok);
                default -> product("Plain gadget").description("Works with any " + tok);
            };
            body.category(category).price(prices[(i / 4) % prices.length]);
            products.put(create(seller, body), body);
        }
        return new Fixture(seller, tok, category, products);
    }

    private record Fixture(TestUser seller, String tok, String category, Map<Long, ProductBody> products) {

        /** Search params of each ordering checked: every sort with the query, plus no query. */
        List<List<String>> orderings() {
            List<List<String>> orderings = new ArrayList<>();
            for (String sort : List.of("relevance", "price_asc", "price_desc", "rating")) {
                orderings.add(List.of("query", tok, "category", category, "sort", sort));
            }
            orderings.add(List.of("category", category, "sort", "relevance"));
            orderings.add(List.of("category", category));
            return orderings;
        }
    }

    /**
     * Reads every page of size {@value #PAGE_SIZE}; with a {@code fixture}, updates the stock of
     * the first product seen and of one not seen yet right after page 0 (an update writes a new
     * row version, which moves it in the table's physical order).
     */
    private List<Long> readAllPages(List<String> params, Fixture fixture) {
        List<Long> all = new ArrayList<>();
        Map<String, Object> first = page(params, 0);
        int totalPages = ((Number) first.get("totalPages")).intValue();
        all.addAll(ids(first));
        if (fixture != null) {
            Long seen = all.get(0);
            Long unseen = fixture.products().keySet().stream().filter(id -> !all.contains(id)).findFirst().orElseThrow();
            updateStock(fixture, seen);
            updateStock(fixture, unseen);
        }
        for (int page = 1; page < totalPages; page++) {
            all.addAll(ids(page(params, page)));
        }
        return all;
    }

    private void assertCompleteAndStable(Fixture fixture, List<String> params, List<Long> paged) {
        Map<String, Object> whole = search(withPaging(params, 0, 100));
        assertThat(new HashSet<>(paged)).as("duplicates in %s", params).hasSize(paged.size());
        assertThat(paged).as("ids of %s", params).containsExactlyInAnyOrderElementsOf(fixture.products().keySet());
        assertThat((long) paged.size()).as("count of %s", params).isEqualTo(asLong(whole.get("totalElements")));
        assertThat(paged).as("order of %s", params).containsExactlyElementsOf(ids(whole));
    }

    private Map<String, Object> page(List<String> params, int page) {
        return search(withPaging(params, page, PAGE_SIZE));
    }

    private static String[] withPaging(List<String> params, int page, int size) {
        List<String> all = new ArrayList<>(params);
        all.addAll(List.of("page", String.valueOf(page), "size", String.valueOf(size)));
        return all.toArray(String[]::new);
    }

    private void updateStock(Fixture fixture, Long id) {
        ProductBody body = fixture.products().get(id);
        body.fields.put("stockQuantity", ((Integer) body.fields.get("stockQuantity")) + 1);
        ResponseEntity<Map<String, Object>> updated = rest.exchange("/api/catalog/products/" + id, HttpMethod.PUT,
                json(body.fields, fixture.seller().token()), MAP);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // --- helpers -------------------------------------------------------------------------------

    /** A random lowercase 8-letter word no real product (or other test) will contain. */
    private static String token() {
        StringBuilder letters = new StringBuilder("zq");
        for (int i = 0; i < 6; i++) {
            letters.append((char) ('a' + ThreadLocalRandom.current().nextInt(26)));
        }
        return letters.toString();
    }

    private static ProductBody product(String name) {
        return new ProductBody(name);
    }

    /** Seller create/update-product body; category defaults to a shared one, price to 10.00. */
    private static final class ProductBody {
        private final Map<String, Object> fields = new HashMap<>();

        ProductBody(String name) {
            fields.put("name", name);
            fields.put("price", new BigDecimal("10.00"));
            fields.put("stockQuantity", 5);
            fields.put("category", "Integration Tests");
        }

        ProductBody brand(String brand) {
            fields.put("brand", brand);
            return this;
        }

        ProductBody category(String category) {
            fields.put("category", category);
            return this;
        }

        ProductBody description(String description) {
            fields.put("description", description);
            return this;
        }

        ProductBody price(String price) {
            fields.put("price", new BigDecimal(price));
            return this;
        }
    }

    private Long create(TestUser seller, ProductBody body) {
        ResponseEntity<Map<String, Object>> created = rest.exchange("/api/catalog/products", HttpMethod.POST,
                json(body.fields, seller.token()), MAP);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        return asLong(created.getBody().get("id"));
    }

    private Map<String, Object> search(String... params) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromHttpUrl(rest.getRootUri() + "/api/catalog/products");
        for (int i = 0; i < params.length; i += 2) {
            uri.queryParam(params[i], params[i + 1]);
        }
        ResponseEntity<Map<String, Object>> page = rest.exchange(
                uri.build().encode().toUri(), HttpMethod.GET, HttpEntity.EMPTY, MAP);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        return page.getBody();
    }

    @SuppressWarnings("unchecked")
    private static List<Long> ids(Map<String, Object> page) {
        return ((List<Map<String, Object>>) page.get("content")).stream()
                .map(product -> asLong(product.get("id")))
                .toList();
    }
}
