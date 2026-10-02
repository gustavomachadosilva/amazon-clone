package com.mercatto.recommendations.service;

import com.mercatto.catalog.service.ProductService.ProductView;
import com.mercatto.recommendations.service.HomeRecommendationRanker.Profile;
import com.mercatto.recommendations.service.HomeRecommendationRanker.Scored;
import com.mercatto.recommendations.service.HomeRecommendationService.HomeLayer;
import com.mercatto.recommendations.service.HomeRecommendationService.HomeRecommendations;
import com.mercatto.recommendations.service.HomeRecommendationService.Item;
import com.mercatto.recommendations.service.HomeRecommendationService.RecommendationReason;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.assertj.core.api.Assertions.tuple;

class HomeRecommendationRankerTest {

    private final HomeRecommendationRanker ranker = new HomeRecommendationRanker();

    private static ProductView view(long id, String category) {
        return view(id, category, 10, 0.0, 0);
    }

    private static ProductView view(long id, String category, Integer stock, double rating, long reviews) {
        return view(id, category, stock, rating, reviews, null);
    }

    private static ProductView view(long id, String category, Integer stock, double rating, long reviews, String brand) {
        return new ProductView(id, "Product " + id, null, BigDecimal.TEN, stock, category, null, brand, 12, null, null,
                1L, null, rating, reviews);
    }

    private static List<Long> ids(HomeRecommendations recommendations) {
        return recommendations.items().stream().map(item -> item.product().id()).toList();
    }

    private static List<Long> ids(List<Scored> scored) {
        return scored.stream().map(item -> item.product().id()).toList();
    }

    private static Scored scored(long id, String category, double score, RecommendationReason reason) {
        return new Scored(view(id, category), reason, score);
    }

    /** {@code count} candidates of {@code category} with ids from {@code firstId}, scores decreasing. */
    private static List<Scored> pool(long firstId, int count, String category, RecommendationReason reason) {
        return IntStream.range(0, count)
                .mapToObj(i -> scored(firstId + i, category, 1.0 - i * 0.01, reason))
                .toList();
    }

    // --- profile ---

    @Test
    void purchasesWeighThreeCartTwoAndListsOne() {
        Profile profile = ranker.profile(List.of(1L), List.of(2L, 3L), List.of(4L, 5L, 6L),
                List.of(view(1, "games"), view(2, "books"), view(3, "books"),
                        view(4, "garden"), view(5, "garden"), view(6, "games")));

        assertThat(profile.categoryWeights()).containsExactlyInAnyOrderEntriesOf(
                Map.of("games", 3 + 1, "books", 2 + 2, "garden", 1 + 1));
        // books and games tie at 4: the name breaks the tie.
        assertThat(profile.topCategories()).containsExactly("books", "games", "garden");
        assertThat(profile.excluded()).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L);
    }

    @Test
    void onlyTheFourStrongestCategoriesAreMined() {
        Profile profile = ranker.profile(List.of(1L, 2L, 3L, 4L), List.of(), List.of(5L),
                List.of(view(1, "a"), view(2, "b"), view(3, "c"), view(4, "d"), view(5, "e")));

        assertThat(profile.topCategories()).containsExactly("a", "b", "c", "d");
    }

    @Test
    void theSameProductInTwoListsCountsOnce() {
        Profile profile = ranker.profile(List.of(), List.of(), List.of(1L, 1L), List.of(view(1, "games")));

        assertThat(profile.categoryWeights()).containsEntry("games", 1);
    }

    @Test
    void aSignalProductGoneFromTheCatalogIsStillExcludedButSaysNothingAboutCategories() {
        Profile profile = ranker.profile(List.of(99L), List.of(), List.of(), List.of());

        assertThat(profile.categoryWeights()).isEmpty();
        assertThat(profile.topCategories()).isEmpty();
        assertThat(profile.excluded()).containsExactly(99L);
        assertThat(profile.hasSignals()).isTrue();
    }

    @Test
    void brandAffinityIgnoresBlankBrandsAndCase() {
        Profile profile = ranker.profile(List.of(1L, 2L), List.of(), List.of(),
                List.of(view(1, "games", 1, 0, 0, " Sony "), view(2, "games", 1, 0, 0, "  ")));

        assertThat(profile.brandWeights()).containsExactly(Map.entry("sony", 3));
        assertThat(profile.brandMatches("SONY")).isTrue();
        assertThat(profile.brandMatches(" ")).isFalse();
        assertThat(profile.brandMatches(null)).isFalse();
    }

    // --- personalized scoring ---

    @Test
    void neverRecommendsPurchasedCartListedOrOutOfStockProducts() {
        Profile profile = ranker.profile(List.of(1L), List.of(2L), List.of(3L),
                List.of(view(1, "games"), view(2, "games"), view(3, "games")));

        List<Scored> scored = ranker.scorePersonalized(profile, Map.of(1L, 1.0, 4L, 1.0, 5L, 1.0),
                List.of(view(1, "games"), view(2, "games"), view(3, "games"),
                        view(4, "games", 0, 5, 10), view(5, "games", null, 5, 10), view(6, "games")),
                Map.of());

        assertThat(ids(scored)).containsExactly(6L);
    }

    @Test
    void aCoPurchasedProductUnknownToTheCatalogIsDropped() {
        Profile profile = ranker.profile(List.of(1L), List.of(), List.of(), List.of(view(1, "games")));

        List<Scored> scored = ranker.scorePersonalized(profile, Map.of(77L, 2.0, 8L, 1.0),
                List.of(view(8, "books")), Map.of());

        assertThat(scored).extracting(item -> item.product().id(), Scored::reason)
                .containsExactly(tuple(8L, RecommendationReason.BOUGHT_TOGETHER));
    }

    @Test
    void coPurchaseOutranksCategoryAffinityAndBothSourcesCountAsBoughtTogether() {
        Profile profile = ranker.profile(List.of(1L), List.of(), List.of(), List.of(view(1, "games")));

        // 2 is both co-purchased and in the buyer's category (listed twice: deduplicated);
        // 3 only co-purchased (another category); 4 only from the category.
        List<Scored> scored = ranker.scorePersonalized(profile, Map.of(2L, 1.5, 3L, 3.0),
                List.of(view(2, "games"), view(3, "books"), view(4, "games"), view(2, "games")), Map.of());

        assertThat(scored).extracting(item -> item.product().id(), Scored::reason).containsExactly(
                tuple(2L, RecommendationReason.BOUGHT_TOGETHER),
                tuple(3L, RecommendationReason.BOUGHT_TOGETHER),
                tuple(4L, RecommendationReason.CATEGORY_AFFINITY));
        // 2: 0.45 × 0.5 + 0.25 × 1 + 0.20 × 3/5; 3: 0.45 × 1 + 0 + 0.20 × 3/5.
        assertThat(scored.get(0).score()).isCloseTo(0.225 + 0.25 + 0.12, offset(1e-9));
        assertThat(scored.get(1).score()).isCloseTo(0.45 + 0.12, offset(1e-9));
    }

    @Test
    void aStrongerCategoryAffinityRanksHigher() {
        Profile profile = ranker.profile(List.of(1L, 2L), List.of(), List.of(3L),
                List.of(view(1, "games"), view(2, "games"), view(3, "books")));

        List<Scored> scored = ranker.scorePersonalized(profile, Map.of(),
                List.of(view(10, "books"), view(11, "games")), Map.of());

        assertThat(ids(scored)).containsExactly(11L, 10L);
    }

    @Test
    void theBayesianRatingPutsOneFiveStarReviewBelowManyGoodOnes() {
        ProductView single = view(1, "games", 5, 5.0, 1);
        ProductView many = view(2, "games", 5, 4.7, 20);

        assertThat(HomeRecommendationRanker.bayesianRating(single)).isLessThan(HomeRecommendationRanker.bayesianRating(many));
        assertThat(HomeRecommendationRanker.bayesianRating(view(3, "games"))).isEqualTo(3.0);

        Profile profile = ranker.profile(List.of(9L), List.of(), List.of(), List.of(view(9, "games")));
        assertThat(ids(ranker.scorePersonalized(profile, Map.of(), List.of(single, many), Map.of())))
                .containsExactly(2L, 1L);
    }

    @Test
    void popularityAndBrandBreakOtherwiseEqualCandidates() {
        Profile profile = ranker.profile(List.of(9L), List.of(), List.of(),
                List.of(view(9, "games", 1, 0, 0, "Sony")));

        List<Scored> scored = ranker.scorePersonalized(profile, Map.of(),
                List.of(view(1, "games"), view(2, "games", 10, 0, 0, "Sony"), view(3, "games")),
                Map.of(3L, 4, 2L, 2, 1L, 2));

        // 2: brand (0.05) + half the top popularity (0.025); 3: top popularity (0.05); 1: 0.025.
        assertThat(ids(scored)).containsExactly(2L, 3L, 1L);
    }

    @Test
    void equalScoresAreOrderedByLowestId() {
        Profile profile = ranker.profile(List.of(9L), List.of(), List.of(), List.of(view(9, "games")));

        assertThat(ids(ranker.scorePersonalized(profile, Map.of(),
                List.of(view(30, "games"), view(10, "games"), view(20, "games")), Map.of())))
                .containsExactly(10L, 20L, 30L);
    }

    // --- fallback scoring ---

    @Test
    void fallbackMixesRatingAndSalesAndLabelsEachSource() {
        List<Scored> scored = ranker.scoreFallback(Profile.EMPTY,
                List.of(view(1, "a", 5, 4.8, 30), view(2, "b", 5, 4.0, 3)),
                List.of(view(3, "c"), view(1, "a", 5, 4.8, 30), view(4, "d", 0, 5, 50)),
                Map.of(3L, 10, 1L, 1));

        // 3: 0.6 × 3.0/5 + 0.4 × 10/10 = 0.76; 1: 0.6 × 4.64/5 + 0.4 × 1/10 ≈ 0.60; 2: 0.6 × 3.5/5 = 0.42.
        // 1 is in both lists: its top-rated label wins. 4 is out of stock.
        assertThat(scored).extracting(item -> item.product().id(), Scored::reason).containsExactly(
                tuple(3L, RecommendationReason.BEST_SELLER),
                tuple(1L, RecommendationReason.TOP_RATED),
                tuple(2L, RecommendationReason.TOP_RATED));
    }

    @Test
    void fallbackStillExcludesTheBuyersOwnProducts() {
        Profile profile = ranker.profile(List.of(1L), List.of(2L), List.of(), List.of());

        assertThat(ids(ranker.scoreFallback(profile, List.of(view(1, "a"), view(3, "a")), List.of(view(2, "b")),
                Map.of()))).containsExactly(3L);
    }

    // --- selection ---

    @Test
    void personalizedShelfTakesAtMostFourPerCategoryAndPadsWithTheFallback() {
        List<Scored> personalized = pool(1, 6, "games", RecommendationReason.CATEGORY_AFFINITY);
        List<Scored> fallback = new ArrayList<>(pool(100, 3, "books", RecommendationReason.TOP_RATED));
        fallback.addAll(pool(200, 3, "garden", RecommendationReason.TOP_RATED));

        HomeRecommendations result = ranker.select(personalized, fallback, 8);

        assertThat(result.layer()).isEqualTo(HomeLayer.PERSONALIZED);
        // Each group best first: the fallback's 100 and 200 tie on score, then the id decides.
        assertThat(ids(result)).containsExactly(1L, 2L, 3L, 4L, 100L, 200L, 101L, 201L);
    }

    @Test
    void fallbackPaddingCountsThePersonalizedItemsAgainstItsCap() {
        List<Scored> personalized = pool(1, 3, "games", RecommendationReason.CATEGORY_AFFINITY);
        List<Scored> fallback = new ArrayList<>(pool(100, 2, "games", RecommendationReason.TOP_RATED));
        fallback.addAll(pool(200, 3, "books", RecommendationReason.TOP_RATED));

        HomeRecommendations result = ranker.select(personalized, fallback, 5);

        assertThat(ids(result)).containsExactly(1L, 2L, 3L, 200L, 201L);
    }

    @Test
    void theCapsAreRelaxedOnlyWhenTheShelfWouldOtherwiseBeShort() {
        List<Scored> personalized = pool(1, 6, "games", RecommendationReason.CATEGORY_AFFINITY);
        List<Scored> fallback = pool(100, 3, "books", RecommendationReason.TOP_RATED);

        // Caps give 4 games + 2 books = 6; the relaxation adds the rest, personalized first.
        HomeRecommendations result = ranker.select(personalized, fallback, 9);

        assertThat(ids(result)).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 100L, 101L, 102L);
        assertThat(result.layer()).isEqualTo(HomeLayer.PERSONALIZED);
    }

    @Test
    void fewerThanThreePersonalizedItemsFallBackToTopRatedOnly() {
        List<Scored> personalized = pool(1, 2, "games", RecommendationReason.BOUGHT_TOGETHER);
        List<Scored> fallback = new ArrayList<>(pool(100, 3, "books", RecommendationReason.TOP_RATED));
        fallback.addAll(pool(200, 3, "garden", RecommendationReason.BEST_SELLER));

        HomeRecommendations result = ranker.select(personalized, fallback, 12);

        assertThat(result.layer()).isEqualTo(HomeLayer.TOP_RATED);
        assertThat(ids(result)).doesNotContain(1L, 2L);
        // Only 4 fit the cap of 2 per category; the relaxation adds the other 2.
        assertThat(ids(result)).containsExactly(100L, 200L, 101L, 201L, 102L, 202L);
    }

    @Test
    void theAnonymousShelfHasAtMostTwoPerCategoryWhenThereAreEnoughCategories() {
        List<Scored> fallback = new ArrayList<>();
        for (int c = 0; c < 7; c++) {
            fallback.addAll(pool(100L * (c + 1), 3, "category " + c, RecommendationReason.TOP_RATED));
        }
        fallback.sort(HomeRecommendationRanker.ORDER);

        HomeRecommendations result = ranker.select(List.of(), fallback, 12);

        assertThat(result.layer()).isEqualTo(HomeLayer.TOP_RATED);
        assertThat(result.items()).hasSize(12);
        Map<String, Long> perCategory = result.items().stream()
                .collect(Collectors.groupingBy(item -> item.product().category(), Collectors.counting()));
        assertThat(perCategory.values()).allSatisfy(count -> assertThat(count).isLessThanOrEqualTo(2));
    }

    @Test
    void aProductInBothPoolsIsShownOnce() {
        List<Scored> personalized = pool(1, 3, "games", RecommendationReason.BOUGHT_TOGETHER);
        List<Scored> fallback = List.of(scored(1, "games", 5.0, RecommendationReason.TOP_RATED),
                scored(50, "books", 0.1, RecommendationReason.TOP_RATED));

        HomeRecommendations result = ranker.select(personalized, fallback, 12);

        assertThat(result.items()).extracting(item -> item.product().id(), Item::reason).containsExactly(
                tuple(1L, RecommendationReason.BOUGHT_TOGETHER),
                tuple(2L, RecommendationReason.BOUGHT_TOGETHER),
                tuple(3L, RecommendationReason.BOUGHT_TOGETHER),
                tuple(50L, RecommendationReason.TOP_RATED));
    }

    @Test
    void theLimitIsRespectedAndASmallLimitCanStillBePersonalized() {
        List<Scored> personalized = pool(1, 6, "games", RecommendationReason.CATEGORY_AFFINITY);
        List<Scored> fallback = pool(100, 3, "books", RecommendationReason.TOP_RATED);

        HomeRecommendations two = ranker.select(personalized, fallback, 2);
        assertThat(two.layer()).isEqualTo(HomeLayer.PERSONALIZED);
        assertThat(ids(two)).containsExactly(1L, 2L);

        assertThat(ranker.select(personalized, fallback, 1).items()).hasSize(1);
    }

    @Test
    void anEmptyCatalogGivesAnEmptyTopRatedShelf() {
        HomeRecommendations result = ranker.select(List.of(), List.of(), 12);

        assertThat(result.layer()).isEqualTo(HomeLayer.TOP_RATED);
        assertThat(result.items()).isEmpty();
    }
}
