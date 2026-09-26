package com.mercatto.integration;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Always-on (no Docker) sanity checks of the search-evaluation fixtures (#219), so a typo in a
 * product name or an edit to the seed CSV fails {@code mvn test} instead of silently skewing the
 * metrics that {@code SearchEvalIT} reports.
 */
class SearchEvalFixturesTest {

    private final List<SeedCatalogCsv.Row> seed = SeedCatalogCsv.read();
    private final Map<String, SeedCatalogCsv.Row> seedByName = seed.stream()
            .collect(Collectors.toMap(SeedCatalogCsv.Row::name, row -> row));
    private final Set<String> seedCategories = seed.stream()
            .map(SeedCatalogCsv.Row::category).collect(Collectors.toSet());

    @Test
    void seedHas500ProductsWithUniqueNames() {
        assertThat(seed).hasSize(500);
        assertThat(seedByName).hasSize(500);
    }

    @Test
    void queriesAreWellFormed() {
        SearchEvalFixtures.QuerySet set = SearchEvalFixtures.queries();

        assertThat(set.pageSize()).isEqualTo(10);
        assertThat(set.queries()).hasSizeBetween(20, 30);
        assertThat(set.queries()).extracting(SearchEvalFixtures.Query::id).doesNotHaveDuplicates();
        assertThat(set.queries()).extracting(SearchEvalFixtures.Query::type)
                .containsAll(SearchEvalFixtures.QUERY_TYPES)
                .allSatisfy(type -> assertThat(SearchEvalFixtures.QUERY_TYPES).contains(type));

        for (SearchEvalFixtures.Query query : set.queries()) {
            assertThat(query.query()).as(query.id()).isNotBlank();
            assertThat(query.relevant()).as(query.id() + " relevant").doesNotHaveDuplicates()
                    .allSatisfy(name -> assertThat(seedByName).as(query.id()).containsKey(name));
            assertThat(query.relevantCategories()).as(query.id() + " categories")
                    .allSatisfy(category -> assertThat(seedCategories).as(query.id()).contains(category));
            if (query.type().equals("no_result")) {
                assertThat(query.relevant()).as(query.id()).isEmpty();
                assertThat(query.relevantCategories()).as(query.id()).isEmpty();
            }
        }
    }

    @Test
    void noResultQueriesReallyMatchNothingInTheSeed() {
        for (SearchEvalFixtures.Query query : SearchEvalFixtures.queries().queries()) {
            if (query.type().equals("no_result")) {
                String needle = query.query().toLowerCase();
                assertThat(seed).as(query.id())
                        .noneMatch(row -> row.name().toLowerCase().contains(needle)
                                || row.category().toLowerCase().contains(needle));
            }
        }
    }

    @Test
    void datasetReferencesOnlyDeclaredBuyersAndSeedProducts() {
        SearchEvalFixtures.Dataset dataset = SearchEvalFixtures.dataset();
        Set<String> buyers = new HashSet<>(dataset.buyers());

        assertThat(dataset.buyers()).doesNotHaveDuplicates().hasSizeBetween(8, 12);
        assertThat(dataset.orders()).isNotEmpty().allSatisfy(order -> {
            assertThat(buyers).contains(order.buyer());
            assertThat(order.items()).isNotEmpty().allSatisfy(item -> {
                assertThat(seedByName).containsKey(item.product());
                assertThat(item.quantity()).isPositive();
            });
            assertThat(order.items()).extracting(SearchEvalFixtures.OrderItem::product).doesNotHaveDuplicates();
        });
        assertThat(dataset.reviews()).isNotEmpty().allSatisfy(review -> {
            assertThat(buyers).contains(review.buyer());
            assertThat(seedByName).containsKey(review.product());
            assertThat(review.stars()).isBetween(1, 5);
            assertThat(review.title()).isNotBlank();
        });
        assertThat(dataset.lists()).isNotEmpty().allSatisfy(list -> {
            assertThat(buyers).contains(list.buyer());
            assertThat(list.name()).isNotBlank();
            assertThat(list.products()).isNotEmpty().doesNotHaveDuplicates()
                    .allSatisfy(name -> assertThat(seedByName).containsKey(name));
        });
    }

    @Test
    void totalDemandNeverExceedsSeedStock() {
        Map<String, Integer> demand = new HashMap<>();
        for (SearchEvalFixtures.Order order : SearchEvalFixtures.dataset().orders()) {
            for (SearchEvalFixtures.OrderItem item : order.items()) {
                demand.merge(item.product(), item.quantity(), Integer::sum);
            }
        }
        demand.forEach((product, quantity) ->
                assertThat(quantity).as(product).isLessThanOrEqualTo(seedByName.get(product).stockQuantity()));
    }

    @Test
    void expectationsArePlantedInTheData() {
        SearchEvalFixtures.Dataset dataset = SearchEvalFixtures.dataset();

        for (SearchEvalFixtures.AlsoBought expectation : dataset.expectations().alsoBought()) {
            assertThat(seedByName).containsKey(expectation.product());
            assertThat(expectation.expected()).isNotEmpty().allSatisfy(other -> {
                long together = dataset.orders().stream()
                        .map(order -> order.items().stream().map(SearchEvalFixtures.OrderItem::product).toList())
                        .filter(products -> products.contains(expectation.product()) && products.contains(other))
                        .count();
                assertThat(together).as(expectation.product() + " + " + other).isGreaterThanOrEqualTo(2);
            });
        }

        for (SearchEvalFixtures.ForBuyer expectation : dataset.expectations().forBuyer()) {
            assertThat(dataset.buyers()).contains(expectation.buyer());
            Set<String> touched = productsTouchedBy(dataset, expectation.buyer());
            assertThat(expectation.heldOut()).isNotEmpty().allSatisfy(product -> {
                assertThat(touched).as(expectation.buyer() + " must never have touched it").doesNotContain(product);
                assertThat(dataset.orders()).as(product + " must be bought by someone else")
                        .anyMatch(order -> order.items().stream().anyMatch(item -> item.product().equals(product)));
            });
        }
    }

    private static Set<String> productsTouchedBy(SearchEvalFixtures.Dataset dataset, String buyer) {
        Set<String> touched = new HashSet<>();
        dataset.orders().stream().filter(order -> order.buyer().equals(buyer))
                .forEach(order -> order.items().forEach(item -> touched.add(item.product())));
        dataset.reviews().stream().filter(review -> review.buyer().equals(buyer))
                .forEach(review -> touched.add(review.product()));
        dataset.lists().stream().filter(list -> list.buyer().equals(buyer))
                .forEach(list -> touched.addAll(list.products()));
        return touched;
    }
}
