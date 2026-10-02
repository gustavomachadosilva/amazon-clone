package com.mercatto.catalog.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductSearchCriteriaTest {

    @Test
    void normalizesBlankTextFiltersAndNullSort() {
        ProductSearchCriteria criteria = new ProductSearchCriteria("  ", "", null, null, null, null);

        assertThat(criteria.query()).isNull();
        assertThat(criteria.category()).isNull();
        assertThat(criteria.sort()).isEqualTo(ProductSort.RELEVANCE);
    }

    @Test
    void acceptsValidBounds() {
        ProductSearchCriteria criteria = new ProductSearchCriteria(
                "tv", "Electronics", BigDecimal.ZERO, BigDecimal.TEN, 5.0, ProductSort.RATING);

        assertThat(criteria.query()).isEqualTo("tv");
        assertThat(criteria.minPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(criteria.maxPrice()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(criteria.minRating()).isEqualTo(5.0);
    }

    @Test
    void acceptsEqualMinAndMaxPrice() {
        new ProductSearchCriteria(null, null, BigDecimal.TEN, BigDecimal.TEN, null, null);
    }

    @Test
    void rejectsNegativePrices() {
        assertThatThrownBy(() -> new ProductSearchCriteria(null, null, BigDecimal.valueOf(-1), null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProductSearchCriteria(null, null, null, BigDecimal.valueOf(-1), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMinPriceGreaterThanMaxPrice() {
        assertThatThrownBy(() -> new ProductSearchCriteria(
                null, null, BigDecimal.valueOf(50), BigDecimal.TEN, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minPrice");
    }

    @Test
    void rejectsMinRatingOutsideZeroToFive() {
        assertThatThrownBy(() -> new ProductSearchCriteria(null, null, null, null, -0.5, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProductSearchCriteria(null, null, null, null, 5.1, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProductSearchCriteria(null, null, null, null, Double.NaN, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
