package com.mercatto.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Free-text product search (#220) against the real PostgreSQL full-text search: name, brand,
 * category and description are searched, every term must match in any order, {@code %}/{@code _}
 * are literal text, accents/plurals are normalized and a typo is corrected only when nothing
 * matched. The container is shared with the other integration tests, so every test searches for
 * its own random token ({@code zq} + random letters, never a real word) and never assumes the rest
 * of the catalog is empty.
 */
class CatalogTextSearchIntegrationTest extends PostgresIntegrationTest {

    @Test
    void searchesBrandCategoryAndDescriptionNotOnlyTheName() {
        TestUser seller = seller();
        String brandToken = token();
        String categoryToken = token();
        String descriptionToken = token();
        Long byBrand = create(seller, product("Plain gadget").brand(brandToken.toUpperCase()));
        Long byCategory = create(seller, product("Plain gadget").category("IT-" + categoryToken));
        Long byDescription = create(seller, product("Plain gadget")
                .description("Made with " + descriptionToken + " fibers"));

        assertThat(ids(search("query", brandToken))).containsExactly(byBrand);
        assertThat(ids(search("query", categoryToken))).containsExactly(byCategory);
        assertThat(ids(search("query", descriptionToken))).containsExactly(byDescription);
    }

    @Test
    void everyTermMustMatchInAnyOrderAndAnyField() {
        TestUser seller = seller();
        String tok = token();
        Long steelKettle = create(seller, product("Electric kettle " + tok).description("Brushed stainless steel body"));
        create(seller, product("Electric kettle " + tok).description("Plastic body"));

        assertThat(ids(search("query", "steel " + tok + " kettle"))).containsExactly(steelKettle);
        assertThat(ids(search("query", "KETTLE STEEL " + tok))).containsExactly(steelKettle);
        assertThat(asLong(search("query", tok + " kettle").get("totalElements"))).isEqualTo(2L);
        assertThat(asLong(search("query", tok + " kettle titanium").get("totalElements"))).isZero();
    }

    @Test
    void percentAndUnderscoreAreLiteralText() {
        TestUser seller = seller();
        String tok = token();
        String category = "IT-" + UUID.randomUUID();
        Long percent = create(seller, product(tok + " shirt 100% cotton").category(category));
        create(seller, product(tok + " shirt 1000 cotton").category(category));
        Long underscore = create(seller, product(tok + "_a sock").category(category));
        create(seller, product(tok + "ba sock").category(category));

        assertThat(ids(search("query", tok + " 100%"))).containsExactly(percent);
        assertThat(ids(search("query", tok + "_a"))).containsExactly(underscore);
        assertThat(ids(search("query", "%", "category", category))).containsExactly(percent);
        assertThat(ids(search("query", "_", "category", category))).containsExactly(underscore);
        assertThat(asLong(search("query", "100%", "category", category).get("totalElements"))).isEqualTo(1L);
        // Quotes and tsquery operators are plain text too (no SQL/tsquery syntax error).
        assertThat(ids(search("query", "'100%'", "category", category))).isEmpty();
        assertThat(ids(search("query", "shirt's & !cotton | 100%", "category", category)))
                .containsExactly(percent);
    }

    @Test
    void accentsAreIgnoredInBothDirections() {
        TestUser seller = seller();
        String accentedTok = token();
        String plainTok = token();
        Long accented = create(seller, product("Crème brûlée " + accentedTok));
        Long plain = create(seller, product("Creme brulee " + plainTok));

        assertThat(ids(search("query", "creme " + accentedTok))).containsExactly(accented);
        assertThat(ids(search("query", "brulee " + accentedTok))).containsExactly(accented);
        assertThat(ids(search("query", "crème " + plainTok))).containsExactly(plain);
    }

    @Test
    void pluralAndSingularMatchEachOther() {
        TestUser seller = seller();
        String tok = token();
        Long laptop = create(seller, product(tok + " Laptop 15 inch"));
        Long boots = create(seller, product(tok + " Hiking Boots"));

        assertThat(ids(search("query", tok + " laptops"))).containsExactly(laptop);
        assertThat(ids(search("query", tok + " boot"))).containsExactly(boots);
    }

    @Test
    void aTypoIsCorrectedOnlyWhenNothingMatched() {
        TestUser seller = seller();
        String tok = token(); // 8 letters: up to 2 edits
        Long product = create(seller, product("Gizmo " + tok));
        String sevenLetters = "zq" + randomLetters(5);
        create(seller, product("Gizmo " + sevenLetters));

        String missingLetter = tok.substring(0, 4) + tok.substring(5);
        assertThat(ids(search("query", missingLetter))).containsExactly(product);
        assertThat(ids(search("query", "gizmo " + missingLetter))).containsExactly(product);

        // 7 letters tolerate a single edit: two substitutions are a different word.
        String twoEdits = sevenLetters.substring(0, 3) + flip(sevenLetters.charAt(3)) + flip(sevenLetters.charAt(4))
                + sevenLetters.substring(5);
        assertThat(asLong(search("query", twoEdits).get("totalElements"))).isZero();
        assertThat(asLong(search("query", "zqxwvjkyx").get("totalElements"))).isZero();
    }

    @Test
    void textComposesWithFiltersSortingAndPaging() {
        TestUser seller = seller();
        String tok = token();
        String category = "IT-" + UUID.randomUUID();
        create(seller, product("Widget " + tok).category(category).price("10.00"));
        Long middle = create(seller, product("Widget " + tok).category(category).price("20.00"));
        Long expensive = create(seller, product("Widget " + tok).category(category).price("30.00"));
        create(seller, product("Widget " + tok).price("25.00")); // other category
        create(seller, product("Widget other").category(category).price("25.00")); // no token

        Map<String, Object> first = search("query", "widgets " + tok, "category", category, "minPrice", "15",
                "sort", "price_asc", "size", "1", "page", "0");
        assertThat(ids(first)).containsExactly(middle);
        assertThat(asLong(first.get("totalElements"))).isEqualTo(2L);
        assertThat(((Number) first.get("totalPages")).intValue()).isEqualTo(2);

        Map<String, Object> second = search("query", "widgets " + tok, "category", category, "minPrice", "15",
                "sort", "price_asc", "size", "1", "page", "1");
        assertThat(ids(second)).containsExactly(expensive);
    }

    @Test
    void relevanceRanksNameAboveCategoryAboveDescription() {
        TestUser seller = seller();
        String tok = token();
        // Created in reverse order of relevance, so id order would get it backwards.
        Long inDescription = create(seller, product("Lamp").description("Pairs with the " + tok + " range"));
        Long inCategory = create(seller, product("Lamp").category("IT-" + tok));
        Long inName = create(seller, product("Lamp " + tok));

        assertThat(ids(search("query", tok))).containsExactly(inName, inCategory, inDescription);
        assertThat(ids(search("query", tok, "sort", "relevance"))).containsExactly(inName, inCategory, inDescription);
    }

    // --- helpers -------------------------------------------------------------------------------

    /** A random lowercase 8-letter word no real product (or other test) will contain. */
    private static String token() {
        return "zq" + randomLetters(6);
    }

    private static String randomLetters(int length) {
        StringBuilder letters = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            letters.append((char) ('a' + ThreadLocalRandom.current().nextInt(26)));
        }
        return letters.toString();
    }

    /** A different letter, far enough in the alphabet to never be the same one. */
    private static char flip(char letter) {
        return (char) ('a' + (letter - 'a' + 13) % 26);
    }

    private static ProductBody product(String name) {
        return new ProductBody(name);
    }

    /** Seller create-product body; category defaults to a shared one, price to 10.00. */
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

    /**
     * GET /api/catalog/products with the given name/value query params, sent as an already
     * encoded {@link java.net.URI} so {@code %} reaches the server as {@code %25}, not as the
     * start of an escape.
     */
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
