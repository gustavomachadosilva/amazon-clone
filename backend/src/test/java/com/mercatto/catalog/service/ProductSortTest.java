package com.mercatto.catalog.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductSortTest {

    @ParameterizedTest
    @CsvSource({
            "relevance, RELEVANCE",
            "price_asc, PRICE_ASC",
            "PRICE_DESC, PRICE_DESC",
            "Rating, RATING"
    })
    void fromParamParsesCaseInsensitively(String value, ProductSort expected) {
        assertThat(ProductSort.fromParam(value)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void fromParamDefaultsToRelevance(String value) {
        assertThat(ProductSort.fromParam(value)).isEqualTo(ProductSort.RELEVANCE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"low", "price", "name,asc"})
    void fromParamRejectsUnknownValues(String value) {
        assertThatThrownBy(() -> ProductSort.fromParam(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid sort");
    }

    @Test
    void everySortEndsWithIdAscendingAsTieBreaker() {
        for (ProductSort sort : ProductSort.values()) {
            Sort.Order last = sort.toSort().stream().reduce((first, second) -> second).orElseThrow();
            assertThat(last).isEqualTo(Sort.Order.asc("id"));
        }
    }

    @Test
    void toSortMapsEachOrdering() {
        assertThat(ProductSort.RELEVANCE.toSort()).isEqualTo(Sort.by(Sort.Order.asc("id")));
        assertThat(ProductSort.PRICE_ASC.toSort())
                .isEqualTo(Sort.by(Sort.Order.asc("price"), Sort.Order.asc("id")));
        assertThat(ProductSort.PRICE_DESC.toSort())
                .isEqualTo(Sort.by(Sort.Order.desc("price"), Sort.Order.asc("id")));
        assertThat(ProductSort.RATING.toSort()).isEqualTo(Sort.by(
                Sort.Order.desc("averageRating"), Sort.Order.desc("reviewCount"), Sort.Order.asc("id")));
    }
}
