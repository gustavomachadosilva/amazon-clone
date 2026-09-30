package com.mercatto.recommendations.service;

import com.mercatto.catalog.service.ProductService.ProductView;
import com.mercatto.recommendations.service.HomeRecommendationService.HomeLayer;
import com.mercatto.recommendations.service.HomeRecommendationService.HomeRecommendations;
import com.mercatto.recommendations.service.HomeRecommendationService.Item;
import com.mercatto.recommendations.service.HomeRecommendationService.RecommendationReason;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Ranks the Home's shelf (Card #225). Pure and deterministic — no Spring, no service calls — so
 * every rule is unit testable in isolation, like orders' {@code CoPurchaseScorer} and catalog's
 * {@code RelatedProductScorer}. {@link HomeRecommendationServiceImpl} gathers the data from the
 * other modules and hands it over in three steps: {@link #profile} (what the buyer is into),
 * {@link #scorePersonalized}/{@link #scoreFallback} (the two candidate pools) and {@link #select}.
 *
 * <p><b>Scores.</b> Ratings are shrunk towards a prior before being compared — a Bayesian average
 * {@code (count × avg + m × C) / (count + m)} with {@code m = 3}, {@code C = 3.0} — so a single
 * 5-star review doesn't outrank 4.7 stars over twenty reviews. A personalized candidate scores
 * {@code 0.45 × co-purchase + 0.25 × category affinity + 0.20 × rating + 0.05 × popularity
 * + 0.05 × brand match}; a fallback one {@code 0.6 × rating + 0.4 × popularity}. Every term is in
 * {@code [0, 1]}: co-purchase and popularity are normalized by the pool's maximum, category
 * affinity by the buyer's top category, the rating by 5. Ties go to the lowest product id.
 */
final class HomeRecommendationRanker {

    // Affinity of one signal towards its product's category and brand.
    static final int PURCHASE_WEIGHT = 3;
    static final int CART_WEIGHT = 2;
    static final int LIST_WEIGHT = 1;
    /** How many of the buyer's categories are mined for candidates. */
    static final int TOP_CATEGORIES = 4;

    static final int MAX_PER_CATEGORY_PERSONALIZED = 4;
    static final int MAX_PER_CATEGORY_FALLBACK = 2;
    /** Fewer personalized items than this isn't a "Recommended for you" shelf: show "Top rated". */
    static final int MIN_PERSONALIZED_ITEMS = 3;

    static final int PRIOR_REVIEWS = 3;
    static final double PRIOR_RATING = 3.0;

    static final double CO_PURCHASE_WEIGHT = 0.45;
    static final double CATEGORY_WEIGHT = 0.25;
    static final double RATING_WEIGHT = 0.20;
    static final double POPULARITY_WEIGHT = 0.05;
    static final double BRAND_WEIGHT = 0.05;

    static final double FALLBACK_RATING_WEIGHT = 0.6;
    static final double FALLBACK_POPULARITY_WEIGHT = 0.4;

    /** Score desc, then lowest product id for a stable result. */
    static final Comparator<Scored> ORDER = Comparator
            .comparingDouble(Scored::score).reversed()
            .thenComparing(scored -> scored.product().id());

    /**
     * What a buyer is into: weighted affinity per category and per (non-blank, lowercased) brand,
     * the {@link #TOP_CATEGORIES} strongest categories (ties by name), and every product the buyer
     * bought, carted or listed — never recommended back.
     */
    record Profile(Map<String, Integer> categoryWeights, Map<String, Integer> brandWeights,
                   List<String> topCategories, Set<Long> excluded) {

        static final Profile EMPTY = new Profile(Map.of(), Map.of(), List.of(), Set.of());

        boolean hasSignals() {
            return !excluded.isEmpty();
        }

        double categoryAffinity(String category) {
            if (topCategories.isEmpty()) {
                return 0.0;
            }
            int top = categoryWeights.get(topCategories.get(0));
            return category == null ? 0.0 : categoryWeights.getOrDefault(category, 0) / (double) top;
        }

        boolean brandMatches(String brand) {
            return brandKey(brand) != null && brandWeights.containsKey(brandKey(brand));
        }
    }

    record Scored(ProductView product, RecommendationReason reason, double score) {}

    /**
     * @param purchasedIds products the buyer bought (each counted once)
     * @param cartIds      products in the buyer's cart, saved-for-later included
     * @param listIds      products in any of the buyer's lists
     * @param signalViews  the catalog view of those products; a deleted product is simply missing
     *                     (it still counts as excluded, but says nothing about categories)
     */
    Profile profile(Collection<Long> purchasedIds, Collection<Long> cartIds, Collection<Long> listIds,
                    Collection<ProductView> signalViews) {
        Map<Long, ProductView> views = new HashMap<>();
        signalViews.forEach(view -> views.put(view.id(), view));

        Map<String, Integer> categoryWeights = new HashMap<>();
        Map<String, Integer> brandWeights = new HashMap<>();
        addSignals(purchasedIds, PURCHASE_WEIGHT, views, categoryWeights, brandWeights);
        addSignals(cartIds, CART_WEIGHT, views, categoryWeights, brandWeights);
        addSignals(listIds, LIST_WEIGHT, views, categoryWeights, brandWeights);

        List<String> topCategories = categoryWeights.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(TOP_CATEGORIES)
                .map(Map.Entry::getKey)
                .toList();

        Set<Long> excluded = new HashSet<>();
        Stream.of(purchasedIds, cartIds, listIds).forEach(excluded::addAll);
        return new Profile(Map.copyOf(categoryWeights), Map.copyOf(brandWeights), topCategories, Set.copyOf(excluded));
    }

    private static void addSignals(Collection<Long> ids, int weight, Map<Long, ProductView> views,
                                   Map<String, Integer> categoryWeights, Map<String, Integer> brandWeights) {
        for (Long id : new LinkedHashSet<>(ids)) {
            ProductView view = views.get(id);
            if (view == null) {
                continue;
            }
            if (view.category() != null) {
                categoryWeights.merge(view.category(), weight, Integer::sum);
            }
            String brand = brandKey(view.brand());
            if (brand != null) {
                brandWeights.merge(brand, weight, Integer::sum);
            }
        }
    }

    /**
     * Scores the personalized pool, best first. A product in {@code coPurchaseScores} is
     * {@link RecommendationReason#BOUGHT_TOGETHER} (even when it was also found through a
     * category), any other one {@link RecommendationReason#CATEGORY_AFFINITY}.
     *
     * @param coPurchaseScores product id → co-purchase score summed over the buyer's anchors
     * @param candidates       the catalog view of every candidate (co-purchased or from a top
     *                         category); a co-purchased id without a view is gone from the catalog
     * @param buyers           product id → distinct buyers (missing means none)
     */
    List<Scored> scorePersonalized(Profile profile, Map<Long, Double> coPurchaseScores,
                                   Collection<ProductView> candidates, Map<Long, Integer> buyers) {
        List<ProductView> eligible = eligible(profile, candidates);
        double maxCoPurchase = eligible.stream()
                .mapToDouble(view -> coPurchaseScores.getOrDefault(view.id(), 0.0))
                .max().orElse(0.0);
        int maxBuyers = maxBuyers(eligible, buyers);

        return eligible.stream()
                .map(view -> {
                    boolean coPurchased = coPurchaseScores.containsKey(view.id());
                    double coNorm = maxCoPurchase > 0 ? coPurchaseScores.getOrDefault(view.id(), 0.0) / maxCoPurchase : 0.0;
                    double score = CO_PURCHASE_WEIGHT * coNorm
                            + CATEGORY_WEIGHT * profile.categoryAffinity(view.category())
                            + RATING_WEIGHT * bayesianRating(view) / 5.0
                            + POPULARITY_WEIGHT * popularity(view, buyers, maxBuyers)
                            + BRAND_WEIGHT * (profile.brandMatches(view.brand()) ? 1.0 : 0.0);
                    RecommendationReason reason = coPurchased
                            ? RecommendationReason.BOUGHT_TOGETHER
                            : RecommendationReason.CATEGORY_AFFINITY;
                    return new Scored(view, reason, score);
                })
                .sorted(ORDER)
                .toList();
    }

    /**
     * Scores the fallback pool, best first: {@code topRated} items are
     * {@link RecommendationReason#TOP_RATED}, the {@code bestSellers} not already among them
     * {@link RecommendationReason#BEST_SELLER}. The profile's exclusions still apply (a buyer
     * without enough history for a personalized shelf still never sees what they already have).
     */
    List<Scored> scoreFallback(Profile profile, Collection<ProductView> topRated, Collection<ProductView> bestSellers,
                               Map<Long, Integer> buyers) {
        Map<Long, RecommendationReason> reasons = new LinkedHashMap<>();
        Map<Long, ProductView> views = new LinkedHashMap<>();
        topRated.forEach(view -> {
            views.putIfAbsent(view.id(), view);
            reasons.putIfAbsent(view.id(), RecommendationReason.TOP_RATED);
        });
        bestSellers.forEach(view -> {
            views.putIfAbsent(view.id(), view);
            reasons.putIfAbsent(view.id(), RecommendationReason.BEST_SELLER);
        });

        List<ProductView> eligible = eligible(profile, views.values());
        int maxBuyers = maxBuyers(eligible, buyers);
        return eligible.stream()
                .map(view -> new Scored(view, reasons.get(view.id()),
                        FALLBACK_RATING_WEIGHT * bayesianRating(view) / 5.0
                                + FALLBACK_POPULARITY_WEIGHT * popularity(view, buyers, maxBuyers)))
                .sorted(ORDER)
                .toList();
    }

    /**
     * Picks the shelf. Personalized candidates are taken greedily, best first, at most
     * {@link #MAX_PER_CATEGORY_PERSONALIZED} per category. With fewer than
     * {@link #MIN_PERSONALIZED_ITEMS} of them (or than {@code limit}, when it's smaller) the shelf
     * is {@link HomeLayer#TOP_RATED} and built from the fallback alone; otherwise it's
     * {@link HomeLayer#PERSONALIZED}, padded with fallback items. Fallback items are taken at most
     * {@link #MAX_PER_CATEGORY_FALLBACK} per category, counting what's already on the shelf. Only
     * when the caps leave the shelf short do the remaining candidates go in regardless of
     * category. Personalized items come first, then the fallback ones, each group best first.
     */
    HomeRecommendations select(List<Scored> personalized, List<Scored> fallback, int limit) {
        Shelf shelf = new Shelf(limit);
        shelf.fill(personalized, MAX_PER_CATEGORY_PERSONALIZED, shelf.personalized);
        boolean isPersonalized = shelf.personalized.size() >= Math.min(MIN_PERSONALIZED_ITEMS, limit);
        if (!isPersonalized) {
            shelf = new Shelf(limit);
        }
        shelf.fill(fallback, MAX_PER_CATEGORY_FALLBACK, shelf.fallback);

        // Relaxation: reached only when the category caps left the shelf short.
        if (isPersonalized) {
            shelf.fill(personalized, Integer.MAX_VALUE, shelf.personalized);
        }
        shelf.fill(fallback, Integer.MAX_VALUE, shelf.fallback);

        List<Item> items = Stream.concat(shelf.personalized.stream().sorted(ORDER), shelf.fallback.stream().sorted(ORDER))
                .map(scored -> new Item(scored.product(), scored.reason()))
                .toList();
        return new HomeRecommendations(isPersonalized ? HomeLayer.PERSONALIZED : HomeLayer.TOP_RATED, items);
    }

    /** The items picked so far, by pool, with the per-category counts the caps are checked against. */
    private static final class Shelf {

        private final int limit;
        private final List<Scored> personalized = new ArrayList<>();
        private final List<Scored> fallback = new ArrayList<>();
        private final Set<Long> ids = new HashSet<>();
        private final Map<String, Integer> perCategory = new HashMap<>();

        private Shelf(int limit) {
            this.limit = limit;
        }

        private void fill(List<Scored> candidates, int maxPerCategory, List<Scored> into) {
            for (Scored candidate : candidates) {
                if (ids.size() >= limit) {
                    return;
                }
                String category = candidate.product().category();
                if (ids.contains(candidate.product().id())
                        || perCategory.getOrDefault(category, 0) >= maxPerCategory) {
                    continue;
                }
                into.add(candidate);
                ids.add(candidate.product().id());
                perCategory.merge(category, 1, Integer::sum);
            }
        }
    }

    /** Rating shrunk towards {@link #PRIOR_RATING} by {@link #PRIOR_REVIEWS} phantom reviews. */
    static double bayesianRating(ProductView view) {
        return (view.reviewCount() * view.averageRating() + PRIOR_REVIEWS * PRIOR_RATING)
                / (view.reviewCount() + PRIOR_REVIEWS);
    }

    /** In stock, not excluded, one view per id. */
    private static List<ProductView> eligible(Profile profile, Collection<ProductView> candidates) {
        Map<Long, ProductView> unique = new LinkedHashMap<>();
        for (ProductView view : candidates) {
            if (view != null && isInStock(view) && !profile.excluded().contains(view.id())) {
                unique.putIfAbsent(view.id(), view);
            }
        }
        return List.copyOf(unique.values());
    }

    private static boolean isInStock(ProductView view) {
        return view.stockQuantity() != null && view.stockQuantity() > 0;
    }

    private static int maxBuyers(List<ProductView> views, Map<Long, Integer> buyers) {
        return views.stream().mapToInt(view -> buyers.getOrDefault(view.id(), 0)).max().orElse(0);
    }

    private static double popularity(ProductView view, Map<Long, Integer> buyers, int maxBuyers) {
        return maxBuyers > 0 ? buyers.getOrDefault(view.id(), 0) / (double) maxBuyers : 0.0;
    }

    private static String brandKey(String brand) {
        return brand == null || brand.isBlank() ? null : brand.trim().toLowerCase(Locale.ROOT);
    }
}
