package com.mercatto.integration;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Loads {@code search-eval/recommendation-dataset.json} into a running application, for the
 * recommendation cards (#223 similar products, #224 frequently bought together, #225 recommended
 * for you) to evaluate against planted co-purchase and taste clusters.
 *
 * <p>Goes exclusively through the public HTTP APIs, as a real client would — register/login,
 * {@code POST /api/orders/checkout}, {@code POST /api/reviews/products/{id}},
 * {@code POST /api/lists} and {@code POST /api/lists/{id}/items} — never through repositories or
 * SQL into other modules' schemas (Contrato de Modularidade; {@code ArchitectureBoundaryTest} also
 * scans tests).
 *
 * <p>Expects the dev seed catalog to be loaded (the test must run with the {@code dev} profile):
 * products are resolved by exact seed name through {@code productIds}.
 */
final class RecommendationDatasetLoader {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    /**
     * What was loaded: the symbolic buyer name → its real user, the seed product name → its id, and
     * the created counts (so a caller can assert the whole dataset went in).
     */
    record LoadedDataset(Map<String, PostgresIntegrationTest.TestUser> buyers, Map<String, Long> productIds,
                         List<Long> orderIds, int reviews, int lists, int listItems) {

        Long productId(String name) {
            return idOf(productIds, name);
        }
    }

    private final PostgresIntegrationTest test;

    RecommendationDatasetLoader(PostgresIntegrationTest test) {
        this.test = test;
    }

    /**
     * @param productIds every seed product name → its id (e.g. built by paging
     *                   {@code GET /api/catalog/products}).
     */
    LoadedDataset load(SearchEvalFixtures.Dataset dataset, Map<String, Long> productIds) {
        Map<String, PostgresIntegrationTest.TestUser> buyers = new LinkedHashMap<>();
        for (String buyer : dataset.buyers()) {
            buyers.put(buyer, test.buyer());
        }
        List<Long> orderIds = new ArrayList<>();

        for (SearchEvalFixtures.Order order : dataset.orders()) {
            List<Map<String, Object>> items = order.items().stream()
                    .map(item -> PostgresIntegrationTest.item(idOf(productIds, item.product()), item.quantity()))
                    .toList();
            ResponseEntity<Map<String, Object>> response = test.checkout(buyers.get(order.buyer()), items, null);
            assertThat(response.getStatusCode()).as("checkout for " + order.buyer()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().get("status")).as("checkout for " + order.buyer()).isEqualTo("PAID");
            orderIds.add(PostgresIntegrationTest.asLong(response.getBody().get("id")));
        }

        int reviews = 0;
        for (SearchEvalFixtures.Review review : dataset.reviews()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("stars", review.stars());
            body.put("title", review.title());
            body.put("text", review.text());
            ResponseEntity<Map<String, Object>> response = test.rest.exchange(
                    "/api/reviews/products/" + idOf(productIds, review.product()), HttpMethod.POST,
                    PostgresIntegrationTest.json(body, buyers.get(review.buyer()).token()), MAP);
            assertThat(response.getStatusCode()).as("review by " + review.buyer()).isEqualTo(HttpStatus.OK);
            reviews++;
        }

        int lists = 0;
        int listItems = 0;
        for (SearchEvalFixtures.WishList list : dataset.lists()) {
            String token = buyers.get(list.buyer()).token();
            ResponseEntity<Map<String, Object>> created = test.rest.exchange("/api/lists", HttpMethod.POST,
                    PostgresIntegrationTest.json(Map.of("name", list.name()), token), MAP);
            assertThat(created.getStatusCode()).as("list of " + list.buyer()).isEqualTo(HttpStatus.OK);
            Long listId = PostgresIntegrationTest.asLong(created.getBody().get("id"));
            lists++;
            for (String product : list.products()) {
                ResponseEntity<Map<String, Object>> added = test.rest.exchange(
                        "/api/lists/" + listId + "/items", HttpMethod.POST,
                        PostgresIntegrationTest.json(Map.of("productId", idOf(productIds, product)), token), MAP);
                assertThat(added.getStatusCode()).as("list item of " + list.buyer()).isEqualTo(HttpStatus.OK);
                listItems++;
            }
        }

        return new LoadedDataset(buyers, productIds, List.copyOf(orderIds), reviews, lists, listItems);
    }

    private static Long idOf(Map<String, Long> productIds, String name) {
        Long id = productIds.get(name);
        if (id == null) {
            throw new IllegalArgumentException("Unknown seed product: " + name);
        }
        return id;
    }
}
