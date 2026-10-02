package com.mercatto.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Product search filters/sorting applied in the database (#222): totals and pagination must
 * describe the whole filtered result, not the current page, and the rating filter/sort must see
 * the rating Catalog keeps in sync with Reviews through {@code ReviewCreatedEvent}. Every test
 * searches inside its own unique category so products created by other tests never interfere.
 */
class CatalogSearchIntegrationTest extends PostgresIntegrationTest {

    @Test
    void priceSortIsGlobalAcrossPages() {
        TestUser seller = seller();
        String category = uniqueCategory();
        Long expensive = createProduct(seller, "30.00", 5, category);
        Long middle = createProduct(seller, "20.00", 5, category);
        Long cheapest = createProduct(seller, "10.00", 5, category);

        Map<String, Object> first = search("category", category, "sort", "price_asc", "size", "2", "page", "0");
        assertThat(ids(first)).containsExactly(cheapest, middle);
        assertThat(asLong(first.get("totalElements"))).isEqualTo(3L);
        assertThat(((Number) first.get("totalPages")).intValue()).isEqualTo(2);

        Map<String, Object> second = search("category", category, "sort", "price_asc", "size", "2", "page", "1");
        assertThat(ids(second)).containsExactly(expensive);

        Map<String, Object> descending = search("category", category, "sort", "price_desc", "size", "2");
        assertThat(ids(descending)).containsExactly(expensive, middle);
    }

    @Test
    void priceRangeIsAppliedBeforePaging() {
        TestUser seller = seller();
        String category = uniqueCategory();
        for (String price : List.of("5.00", "15.00", "25.00", "35.00", "45.00")) {
            createProduct(seller, price, 5, category);
        }

        Map<String, Object> page = search("category", category, "minPrice", "10", "maxPrice", "40",
                "sort", "price_asc", "size", "2");

        assertThat(asLong(page.get("totalElements"))).isEqualTo(3L);
        assertThat(((Number) page.get("totalPages")).intValue()).isEqualTo(2);
        assertThat(prices(page)).containsExactly(new BigDecimal("15.00"), new BigDecimal("25.00"));
    }

    @Test
    void reviewsUpdateTheRatingThatSearchFiltersAndSortsBy() {
        TestUser seller = seller();
        TestUser buyer = buyer();
        String category = uniqueCategory();
        Long unrated = createProduct(seller, "10.00", 5, category);
        Long average = createProduct(seller, "10.00", 5, category);
        Long best = createProduct(seller, "10.00", 5, category);

        postReview(buyer, best, 5);
        postReview(buyer, average, 4);
        postReview(buyer, average, 3);

        Map<String, Object> bestProduct = getProduct(best);
        assertThat(((Number) bestProduct.get("averageRating")).doubleValue()).isEqualTo(5.0);
        assertThat(asLong(bestProduct.get("reviewCount"))).isEqualTo(1L);
        Map<String, Object> averageProduct = getProduct(average);
        assertThat(((Number) averageProduct.get("averageRating")).doubleValue()).isEqualTo(3.5);
        assertThat(asLong(averageProduct.get("reviewCount"))).isEqualTo(2L);

        Map<String, Object> fourAndUp = search("category", category, "minRating", "4");
        assertThat(ids(fourAndUp)).containsExactly(best);
        assertThat(asLong(fourAndUp.get("totalElements"))).isEqualTo(1L);

        Map<String, Object> byRating = search("category", category, "sort", "rating");
        assertThat(ids(byRating)).containsExactly(best, average, unrated);
    }

    @Test
    void sellerEditAfterAReviewKeepsTheRating() {
        TestUser seller = seller();
        TestUser buyer = buyer();
        String category = uniqueCategory();
        Long product = createProduct(seller, "10.00", 5, category);
        postReview(buyer, product, 4);

        Map<String, Object> changes = Map.of(
                "name", "IT product renamed " + UUID.randomUUID(),
                "price", new BigDecimal("12.00"),
                "stockQuantity", 7,
                "category", category);
        ResponseEntity<Map<String, Object>> updated = rest.exchange("/api/catalog/products/" + product,
                HttpMethod.PUT, json(changes, seller.token()), MAP);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> after = getProduct(product);
        assertThat(((Number) after.get("averageRating")).doubleValue()).isEqualTo(4.0);
        assertThat(asLong(after.get("reviewCount"))).isEqualTo(1L);
        assertThat(ids(search("category", category, "minRating", "4"))).containsExactly(product);
    }

    @Test
    void invalidSortIsRejectedWith400() {
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                "/api/catalog/products?sort=bogus", HttpMethod.GET, HttpEntity.EMPTY, MAP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private static String uniqueCategory() {
        return "IT-" + UUID.randomUUID();
    }

    private void postReview(TestUser buyer, Long productId, int stars) {
        ResponseEntity<Map<String, Object>> created = rest.exchange("/api/reviews/products/" + productId,
                HttpMethod.POST, json(Map.of("stars", stars, "title", "IT review", "text", "ok"), buyer.token()),
                MAP);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private Map<String, Object> getProduct(Long productId) {
        ResponseEntity<Map<String, Object>> product = rest.exchange(
                "/api/catalog/products/" + productId, HttpMethod.GET, HttpEntity.EMPTY, MAP);
        assertThat(product.getStatusCode()).isEqualTo(HttpStatus.OK);
        return product.getBody();
    }

    /** GET /api/catalog/products with the given name/value query params. */
    private Map<String, Object> search(String... params) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromPath("/api/catalog/products");
        for (int i = 0; i < params.length; i += 2) {
            uri.queryParam(params[i], params[i + 1]);
        }
        ResponseEntity<Map<String, Object>> page = rest.exchange(
                uri.toUriString(), HttpMethod.GET, HttpEntity.EMPTY, MAP);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        return page.getBody();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> content(Map<String, Object> page) {
        return (List<Map<String, Object>>) page.get("content");
    }

    private static List<Long> ids(Map<String, Object> page) {
        return content(page).stream().map(product -> asLong(product.get("id"))).toList();
    }

    private static List<BigDecimal> prices(Map<String, Object> page) {
        return content(page).stream().map(product -> asMoney(product.get("price")).setScale(2)).toList();
    }
}
