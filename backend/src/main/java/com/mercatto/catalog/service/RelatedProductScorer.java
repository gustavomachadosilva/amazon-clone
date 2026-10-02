package com.mercatto.catalog.service;

import com.mercatto.catalog.service.ProductService.ProductView;
import com.mercatto.catalog.service.ProductService.RelatedProduct;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Scores how related a candidate product is to the one being viewed and lists the reasons that
 * hold (Card #223). Pure and deterministic — no Spring, no repository — so every rule is unit
 * testable in isolation.
 *
 * <p>Score components (max 9.5): same category 3.0, same brand 2.0, name similarity up to 2.0
 * (Jaccard over name tokens), price proximity up to 1.5 (full points at the same price, zero at a
 * difference of 100% or more of the current price) and rating up to 1.0 (only when the candidate
 * has reviews).
 */
final class RelatedProductScorer {

    static final double CATEGORY_POINTS = 3.0;
    static final double BRAND_POINTS = 2.0;
    static final double NAME_POINTS = 2.0;
    static final double PRICE_POINTS = 1.5;
    static final double RATING_POINTS = 1.0;

    // Two shared tokens, not one: a single common word ("black", "steel") says little on its own.
    static final int SIMILAR_NAME_MIN_SHARED_TOKENS = 2;

    private static final int MIN_TOKEN_LENGTH = 3;

    // Words too generic to make two names similar (or to identify a product in a name search).
    private static final Set<String> NAME_STOPWORDS = Set.of(
            "the", "and", "for", "with", "from", "into", "your", "you", "our", "this", "that", "are", "all",
            "pack", "packs", "set", "sets", "new", "inch", "inches", "piece", "pieces", "pcs", "count", "size",
            "men", "mens", "women", "womens", "kids", "kid", "boys", "girls", "baby", "unisex", "adult", "adults");

    // The seed (AmazonProductSeeder#guessBrand) guesses the brand from the first word of the
    // product name, so many "brands" are really a gender, a pack size or an adjective ("Women's",
    // "3-Pack", "Wireless"). Matching on those would claim "More from Women's", so they never
    // count as a brand. Anything containing a digit is rejected too (pack sizes, dimensions).
    private static final Set<String> NON_BRAND_WORDS = Set.of(
            "women's", "womens", "men's", "mens", "kids", "kids'", "kid's", "boys", "boys'", "boy's",
            "girls", "girls'", "girl's", "children's", "baby", "toddler", "unisex", "adult",
            "the", "a", "an", "new", "pack", "set", "wireless", "portable", "premium", "large", "small",
            "mini", "stainless", "adjustable", "universal", "professional", "original");

    /** Relation order: score desc, then more-reviewed first, then lowest id for a stable result. */
    static final Comparator<Scored> ORDER = Comparator
            .comparingDouble(Scored::score).reversed()
            .thenComparing(Comparator.comparingLong((Scored s) -> s.product().reviewCount()).reversed())
            .thenComparing(s -> s.product().id(), Comparator.nullsLast(Comparator.naturalOrder()));

    /**
     * A scored candidate. {@code score} is kept unrounded so sorting isn't affected by rounding;
     * {@link #toRelatedProduct()} rounds it for the API payload.
     */
    record Scored(ProductView product, double score, List<RelatedReason> reasons) {

        RelatedProduct toRelatedProduct() {
            RelatedReason primary = reasons.isEmpty() ? null : reasons.get(0);
            return new RelatedProduct(product, Math.round(score * 1000) / 1000.0, primary, reasons);
        }

        boolean hasAnyReason(Set<RelatedReason> wanted) {
            return reasons.stream().anyMatch(wanted::contains);
        }
    }

    /**
     * @param categoryTopRating best average rating among reviewed products of the current
     *                          product's category, or {@code null} when none is reviewed
     */
    Scored score(ProductView current, ProductView candidate, Double categoryTopRating) {
        boolean sameCategory = Objects.equals(current.category(), candidate.category());
        boolean sameBrand = sameBrand(current.brand(), candidate.brand());
        Set<String> currentTokens = nameTokens(current.name());
        Set<String> candidateTokens = nameTokens(candidate.name());
        int sharedTokens = intersection(currentTokens, candidateTokens).size();

        double score = 0;
        if (sameCategory) {
            score += CATEGORY_POINTS;
        }
        if (sameBrand) {
            score += BRAND_POINTS;
        }
        score += NAME_POINTS * jaccard(currentTokens, candidateTokens, sharedTokens);
        score += PRICE_POINTS * priceProximity(current.price(), candidate.price());
        if (candidate.reviewCount() > 0) {
            score += RATING_POINTS * candidate.averageRating() / 5.0;
        }

        EnumSet<RelatedReason> reasons = EnumSet.noneOf(RelatedReason.class);
        if (sameCategory && candidate.reviewCount() >= 1 && categoryTopRating != null
                && candidate.averageRating() >= categoryTopRating) {
            reasons.add(RelatedReason.TOP_RATED_IN_CATEGORY);
        }
        if (sameCategory && current.price() != null && candidate.price() != null
                && candidate.price().compareTo(current.price()) < 0) {
            reasons.add(RelatedReason.LOWER_PRICE);
        }
        if (sameBrand) {
            reasons.add(RelatedReason.SAME_BRAND);
        }
        if (sharedTokens >= SIMILAR_NAME_MIN_SHARED_TOKENS) {
            reasons.add(RelatedReason.SIMILAR_NAME);
        }
        if (candidate.reviewCount() >= 1 && candidate.averageRating() > current.averageRating()) {
            reasons.add(RelatedReason.HIGHER_RATED);
        }
        if (sameCategory) {
            reasons.add(RelatedReason.SAME_CATEGORY);
        }
        // EnumSet iterates in declaration order, which is the priority order.
        return new Scored(candidate, score, List.copyOf(reasons));
    }

    static boolean sameBrand(String a, String b) {
        return isRealBrand(a) && isRealBrand(b) && a.trim().equalsIgnoreCase(b.trim());
    }

    /** Non-blank, digit-free and not a generic word the seed mistook for a brand. */
    static boolean isRealBrand(String brand) {
        if (brand == null || brand.isBlank()) {
            return false;
        }
        String normalized = brand.trim().toLowerCase(Locale.ROOT).replace('’', '\'');
        if (normalized.chars().anyMatch(Character::isDigit)) {
            return false;
        }
        return !NON_BRAND_WORDS.contains(normalized) && !NAME_STOPWORDS.contains(normalized);
    }

    /** Lowercased name words of 3+ characters, minus stopwords and pure numbers. */
    static Set<String> nameTokens(String name) {
        if (name == null || name.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(name.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(token -> token.length() >= MIN_TOKEN_LENGTH)
                .filter(token -> !NAME_STOPWORDS.contains(token))
                .filter(token -> !token.chars().allMatch(Character::isDigit))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** The {@code max} longest name tokens — the most specific words to search other categories with. */
    static List<String> longestNameTokens(String name, int max) {
        List<String> tokens = new ArrayList<>(nameTokens(name));
        tokens.sort(Comparator.comparingInt(String::length).reversed());
        return tokens.subList(0, Math.min(max, tokens.size()));
    }

    private static Set<String> intersection(Set<String> a, Set<String> b) {
        Set<String> shared = new HashSet<>(a);
        shared.retainAll(b);
        return shared;
    }

    private static double jaccard(Set<String> a, Set<String> b, int sharedTokens) {
        int union = a.size() + b.size() - sharedTokens;
        return union == 0 ? 0.0 : (double) sharedTokens / union;
    }

    private static double priceProximity(BigDecimal current, BigDecimal candidate) {
        if (current == null || candidate == null || current.signum() <= 0) {
            return 0.0;
        }
        double p = current.doubleValue();
        return Math.max(0.0, 1.0 - Math.abs(candidate.doubleValue() - p) / p);
    }
}
