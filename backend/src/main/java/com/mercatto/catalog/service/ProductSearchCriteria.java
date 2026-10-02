package com.mercatto.catalog.service;

import java.math.BigDecimal;

/**
 * Filters and ordering of a product search. Every filter is optional ({@code null} = not
 * applied); blank {@code query}/{@code category} are normalized to {@code null} and a
 * {@code null} sort to {@link ProductSort#RELEVANCE}.
 *
 * @throws IllegalArgumentException for a negative price, {@code minPrice > maxPrice} or a
 *                                  {@code minRating} outside 0..5 (answered with 400)
 */
public record ProductSearchCriteria(String query, String category, BigDecimal minPrice, BigDecimal maxPrice,
                                    Double minRating, ProductSort sort) {

    public ProductSearchCriteria {
        query = blankToNull(query);
        category = blankToNull(category);
        if (minPrice != null && minPrice.signum() < 0) {
            throw new IllegalArgumentException("minPrice must be >= 0");
        }
        if (maxPrice != null && maxPrice.signum() < 0) {
            throw new IllegalArgumentException("maxPrice must be >= 0");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException("minPrice must be <= maxPrice");
        }
        if (minRating != null && (minRating.isNaN() || minRating < 0 || minRating > 5)) {
            throw new IllegalArgumentException("minRating must be between 0 and 5");
        }
        if (sort == null) {
            sort = ProductSort.RELEVANCE;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
