package com.mercatto.integration;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Related products (#223) against real PostgreSQL: the same-category pool query, the
 * cross-category brand/name fallback and the in-stock/self exclusion. Names, brands and
 * categories are random words so products created by other tests never match.
 */
class RelatedProductsIntegrationTest extends PostgresIntegrationTest {

    private static final ParameterizedTypeReference<List<Map<String, Object>>> RELATED =
            new ParameterizedTypeReference<>() {};

    @Test
    void relatedProductsComeFromTheCategoryThenFromBrandOrNameMatchesElsewhere() {
        TestUser seller = seller();
        String category = randomWord();
        String otherCategory = randomWord();
        String brand = randomWord();
        String nameWord1 = randomWord();
        String nameWord2 = randomWord();

        Long current = createProduct(seller, nameWord1 + " " + nameWord2 + " Drill", brand, "100.00", 5, category);
        Long cheaper = createProduct(seller, "Garden Hose " + randomWord(), null, "80.00", 5, category);
        createProduct(seller, "Sold Out " + randomWord(), null, "90.00", 0, category);
        Long sameBrand = createProduct(seller, "Leaf Blower " + randomWord(), brand.toUpperCase(), "300.00", 5,
                otherCategory);
        Long similarName = createProduct(seller, nameWord2 + " " + nameWord1 + " Holster", null, "20.00", 5,
                otherCategory);
        createProduct(seller, "Kitchen Knife " + randomWord(), null, "100.00", 5, otherCategory);

        List<Map<String, Object>> related = related(current);

        assertThat(related).extracting(r -> asLong(product(r).get("id")))
                .containsExactlyInAnyOrder(cheaper, sameBrand, similarName);
        assertThat(reasonOf(related, cheaper)).isEqualTo("LOWER_PRICE");
        assertThat(reasonOf(related, sameBrand)).isEqualTo("SAME_BRAND");
        assertThat(reasonOf(related, similarName)).isEqualTo("SIMILAR_NAME");
    }

    @Test
    void unknownProductReturns404() {
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                "/api/catalog/products/" + Long.MAX_VALUE + "/related", HttpMethod.GET, HttpEntity.EMPTY, MAP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // Letters only: a brand containing a digit is never treated as a real brand by the scorer.
    private static String randomWord() {
        StringBuilder word = new StringBuilder("w");
        for (char c : UUID.randomUUID().toString().replace("-", "").toCharArray()) {
            word.append(Character.isDigit(c) ? (char) ('g' + (c - '0')) : c);
        }
        return word.toString();
    }

    private Long createProduct(TestUser seller, String name, String brand, String price, int stock,
                               String category) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("brand", brand);
        body.put("price", new BigDecimal(price));
        body.put("stockQuantity", stock);
        body.put("category", category);
        ResponseEntity<Map<String, Object>> created = rest.exchange("/api/catalog/products", HttpMethod.POST,
                json(body, seller.token()), MAP);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        return asLong(created.getBody().get("id"));
    }

    private List<Map<String, Object>> related(Long productId) {
        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                "/api/catalog/products/" + productId + "/related", HttpMethod.GET, HttpEntity.EMPTY, RELATED);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> product(Map<String, Object> related) {
        return (Map<String, Object>) related.get("product");
    }

    private static Object reasonOf(List<Map<String, Object>> related, Long productId) {
        return related.stream()
                .filter(r -> asLong(product(r).get("id")).equals(productId))
                .findFirst()
                .orElseThrow()
                .get("primaryReason");
    }
}
