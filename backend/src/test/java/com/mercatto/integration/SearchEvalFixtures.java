package com.mercatto.integration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * The two versioned fixtures of the search/recommendation evaluation (#219), under
 * {@code src/test/resources/search-eval/}:
 *
 * <ul>
 *   <li>{@code queries.json}: reference search queries with the seed products judged relevant.</li>
 *   <li>{@code recommendation-dataset.json}: fictional buyers, orders, reviews and lists for the
 *       recommendation cards (#223–#225), loaded by {@link RecommendationDatasetLoader}.</li>
 * </ul>
 *
 * Products are referenced by exact seed name ({@code seed/amazon-products-sample.csv}; all 500
 * names are unique) because database ids depend on insertion order.
 */
final class SearchEvalFixtures {

    static final String QUERIES_PATH = "search-eval/queries.json";
    static final String DATASET_PATH = "search-eval/recommendation-dataset.json";

    /** Every query {@code type} the reference set must cover. */
    static final List<String> QUERY_TYPES = List.of(
            "exact", "multi_term", "plural_singular", "typo", "category_only", "special_chars", "no_result");

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    record QuerySet(int version, String description, int pageSize, List<Query> queries) {}

    /**
     * A reference query. Its relevant set is the union of {@code relevant} (product names) and every
     * product in {@code relevantCategories}; an empty union means "nothing should come back".
     */
    record Query(String id, String type, String query, List<String> relevant, List<String> relevantCategories,
                 String notes) {}

    record Dataset(int version, String description, List<String> buyers, List<Order> orders,
                   List<Review> reviews, List<WishList> lists, Expectations expectations) {}

    record Order(String buyer, List<OrderItem> items) {}

    record OrderItem(String product, int quantity) {}

    record Review(String buyer, String product, int stars, String title, String text) {}

    record WishList(String buyer, String name, List<String> products) {}

    record Expectations(List<AlsoBought> alsoBought, List<ForBuyer> forBuyer) {}

    /** Products a "frequently bought together" for {@code product} should surface (#224). */
    record AlsoBought(String product, List<String> expected, String notes) {}

    /** Products a personalised feed for {@code buyer} should surface, never interacted with (#225). */
    record ForBuyer(String buyer, List<String> heldOut, String notes) {}

    private SearchEvalFixtures() {}

    static QuerySet queries() {
        return read(QUERIES_PATH, QuerySet.class);
    }

    static Dataset dataset() {
        return read(DATASET_PATH, Dataset.class);
    }

    private static <T> T read(String path, Class<T> type) {
        try (InputStream in = SearchEvalFixtures.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Fixture not found on the classpath: " + path);
            }
            return MAPPER.readValue(in, type);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not parse " + path, e);
        }
    }
}
