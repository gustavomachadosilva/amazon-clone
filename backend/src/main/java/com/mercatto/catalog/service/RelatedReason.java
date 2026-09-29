package com.mercatto.catalog.service;

/**
 * Why a product was recommended as related to another one (Card #223). Every value is a fact
 * checked against the catalog data at scoring time — never a guess — so the storefront can show
 * it verbatim. Declared in priority order: a recommendation's primary reason is the first one
 * here that holds.
 */
public enum RelatedReason {

    /**
     * Same category, has at least one review, and its average rating equals the best average
     * rating among the reviewed products of that category (the current product included).
     */
    TOP_RATED_IN_CATEGORY,

    /** Same category and strictly cheaper than the current product. */
    LOWER_PRICE,

    /**
     * Same brand as the current product (case-insensitive), ignoring values that are not really
     * a brand — see {@code RelatedProductScorer#isRealBrand}.
     */
    SAME_BRAND,

    /** Shares at least two meaningful name tokens (stopwords and numbers ignored). */
    SIMILAR_NAME,

    /** Has at least one review and a strictly higher average rating than the current product. */
    HIGHER_RATED,

    /** Same category as the current product. */
    SAME_CATEGORY
}
