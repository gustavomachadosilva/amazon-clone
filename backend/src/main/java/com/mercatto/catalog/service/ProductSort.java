package com.mercatto.catalog.service;

import org.springframework.data.domain.Sort;

import java.util.Locale;

/**
 * Orderings accepted by the product search ({@code ?sort=} on {@code GET /api/catalog/products}).
 * Every ordering ends with {@code id ASC} so ties (same price, same rating) come back in a stable
 * order and a product never shows up on two pages, or on none.
 */
public enum ProductSort {

    // No real relevance score exists yet: "relevance" is the catalog's natural (id) order.
    RELEVANCE(Sort.by(Sort.Order.asc("id"))),
    PRICE_ASC(Sort.by(Sort.Order.asc("price"), Sort.Order.asc("id"))),
    PRICE_DESC(Sort.by(Sort.Order.desc("price"), Sort.Order.asc("id"))),
    RATING(Sort.by(Sort.Order.desc("averageRating"), Sort.Order.desc("reviewCount"), Sort.Order.asc("id")));

    private final Sort sort;

    ProductSort(Sort sort) {
        this.sort = sort;
    }

    public Sort toSort() {
        return sort;
    }

    /**
     * Parses the query-string value ({@code relevance|price_asc|price_desc|rating}, any case);
     * {@code null}/blank means {@link #RELEVANCE}.
     *
     * @throws IllegalArgumentException for any other value (answered with 400)
     */
    public static ProductSort fromParam(String value) {
        if (value == null || value.isBlank()) {
            return RELEVANCE;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid sort: " + value);
        }
    }
}
