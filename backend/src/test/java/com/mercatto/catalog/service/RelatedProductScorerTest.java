package com.mercatto.catalog.service;

import com.mercatto.catalog.service.ProductService.ProductView;
import com.mercatto.catalog.service.ProductService.RelatedProduct;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RelatedProductScorerTest {

    private final RelatedProductScorer scorer = new RelatedProductScorer();

    private static ProductView view(long id, String name, String category, String brand, String price,
                                    double averageRating, long reviewCount) {
        return new ProductView(id, name, "desc", new BigDecimal(price), 5, category, null, brand, 12, null,
                null, 1L, Instant.parse("2026-01-01T00:00:00Z"), averageRating, reviewCount);
    }

    // Current product: no reviews, so HIGHER_RATED only depends on the candidate having reviews.
    private static final ProductView CURRENT = view(1L, "Acme Cordless Drill Kit", "tools", "Acme", "100", 0.0, 0L);

    private List<RelatedReason> reasons(ProductView candidate, Double categoryTopRating) {
        return scorer.score(CURRENT, candidate, categoryTopRating).reasons();
    }

    private List<RelatedReason> reasons(ProductView candidate) {
        return reasons(candidate, null);
    }

    // --- SAME_CATEGORY ---

    @Test
    void sameCategory_whenCategoriesMatch() {
        assertThat(reasons(view(2L, "Garden Hose", "tools", null, "100", 0, 0)))
                .containsExactly(RelatedReason.SAME_CATEGORY);
    }

    @Test
    void sameCategory_notWhenCategoriesDiffer() {
        assertThat(reasons(view(2L, "Garden Hose", "garden", null, "100", 0, 0))).isEmpty();
    }

    // --- LOWER_PRICE ---

    @Test
    void lowerPrice_whenSameCategoryAndCheaper() {
        assertThat(reasons(view(2L, "Garden Hose", "tools", null, "99.99", 0, 0)))
                .contains(RelatedReason.LOWER_PRICE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"100", "100.00", "150"})
    void lowerPrice_notWhenEqualOrPricier(String price) {
        assertThat(reasons(view(2L, "Garden Hose", "tools", null, price, 0, 0)))
                .doesNotContain(RelatedReason.LOWER_PRICE);
    }

    @Test
    void lowerPrice_notWhenCheaperButInAnotherCategory() {
        assertThat(reasons(view(2L, "Garden Hose", "garden", null, "10", 0, 0)))
                .doesNotContain(RelatedReason.LOWER_PRICE);
    }

    // --- SAME_BRAND ---

    @Test
    void sameBrand_matchesIgnoringCaseAndSurroundingSpaces() {
        assertThat(reasons(view(2L, "Garden Hose", "garden", "  ACME ", "100", 0, 0)))
                .containsExactly(RelatedReason.SAME_BRAND);
    }

    @Test
    void sameBrand_notWhenBrandMissing() {
        assertThat(reasons(view(2L, "Garden Hose", "tools", null, "100", 0, 0)))
                .doesNotContain(RelatedReason.SAME_BRAND);
        assertThat(reasons(view(3L, "Garden Hose", "tools", "  ", "100", 0, 0)))
                .doesNotContain(RelatedReason.SAME_BRAND);
    }

    @Test
    void sameBrand_notWhenBrandsDiffer() {
        assertThat(reasons(view(2L, "Garden Hose", "tools", "Bosch", "100", 0, 0)))
                .doesNotContain(RelatedReason.SAME_BRAND);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Women's", "Men's", "Kids'", "Baby", "3-Pack", "Wireless", "the", "2024"})
    void sameBrand_notWhenTheSharedBrandIsAGenericWordTheSeedGuessed(String brand) {
        ProductView current = view(1L, brand + " Thing", "tools", brand, "100", 0, 0);
        ProductView candidate = view(2L, brand + " Other", "garden", brand.toUpperCase(), "100", 0, 0);

        assertThat(scorer.score(current, candidate, null).reasons()).doesNotContain(RelatedReason.SAME_BRAND);
        assertThat(RelatedProductScorer.isRealBrand(brand)).isFalse();
    }

    // --- SIMILAR_NAME ---

    @Test
    void similarName_whenTwoTokensShared() {
        assertThat(reasons(view(2L, "Makita cordless drill", "garden", null, "100", 0, 0)))
                .containsExactly(RelatedReason.SIMILAR_NAME);
    }

    @Test
    void similarName_notWhenOnlyOneTokenShared() {
        assertThat(reasons(view(2L, "Hammer Drill", "garden", null, "100", 0, 0)))
                .doesNotContain(RelatedReason.SIMILAR_NAME);
    }

    @Test
    void similarName_notWhenOnlyStopwordsAndNumbersShared() {
        ProductView current = view(1L, "The New 12 Pack for Women", "tools", null, "100", 0, 0);
        ProductView candidate = view(2L, "The new 12 pack for women", "garden", null, "100", 0, 0);

        assertThat(scorer.score(current, candidate, null).reasons()).doesNotContain(RelatedReason.SIMILAR_NAME);
    }

    // --- HIGHER_RATED ---

    @Test
    void higherRated_whenReviewedAndRatedAboveCurrent() {
        ProductView current = view(1L, "Drill", "tools", null, "100", 3.5, 4);
        ProductView candidate = view(2L, "Hose", "garden", null, "100", 4.0, 1);

        assertThat(scorer.score(current, candidate, null).reasons()).containsExactly(RelatedReason.HIGHER_RATED);
    }

    @Test
    void higherRated_notWhenEqualOrLowerRating() {
        ProductView current = view(1L, "Drill", "tools", null, "100", 4.0, 4);

        assertThat(scorer.score(current, view(2L, "Hose", "garden", null, "100", 4.0, 9), null).reasons())
                .doesNotContain(RelatedReason.HIGHER_RATED);
        assertThat(scorer.score(current, view(3L, "Hose", "garden", null, "100", 3.0, 9), null).reasons())
                .doesNotContain(RelatedReason.HIGHER_RATED);
    }

    @Test
    void higherRated_notWhenCandidateHasNoReviews() {
        // A rating with zero reviews is just the column default, not a customer opinion.
        assertThat(reasons(view(2L, "Hose", "garden", null, "100", 5.0, 0)))
                .doesNotContain(RelatedReason.HIGHER_RATED);
    }

    // --- TOP_RATED_IN_CATEGORY ---

    @Test
    void topRatedInCategory_whenRatingEqualsTheCategoryMax() {
        assertThat(reasons(view(2L, "Hose", "tools", null, "100", 4.8, 3), 4.8))
                .contains(RelatedReason.TOP_RATED_IN_CATEGORY);
    }

    @Test
    void topRatedInCategory_notWhenBelowTheCategoryMax() {
        assertThat(reasons(view(2L, "Hose", "tools", null, "100", 4.7, 3), 4.8))
                .doesNotContain(RelatedReason.TOP_RATED_IN_CATEGORY);
    }

    @Test
    void topRatedInCategory_notWhenCandidateHasNoReviews() {
        assertThat(reasons(view(2L, "Hose", "tools", null, "100", 0.0, 0), 0.0))
                .doesNotContain(RelatedReason.TOP_RATED_IN_CATEGORY);
    }

    @Test
    void topRatedInCategory_notWhenCategoryHasNoReviewedProduct() {
        assertThat(reasons(view(2L, "Hose", "tools", null, "100", 4.8, 3), null))
                .doesNotContain(RelatedReason.TOP_RATED_IN_CATEGORY);
    }

    @Test
    void topRatedInCategory_notWhenInAnotherCategory() {
        assertThat(reasons(view(2L, "Hose", "garden", null, "100", 4.8, 3), 4.8))
                .doesNotContain(RelatedReason.TOP_RATED_IN_CATEGORY);
    }

    // --- priority and score ---

    @Test
    void reasonsAreListedInPriorityOrderAndPrimaryIsTheFirst() {
        ProductView candidate = view(2L, "Acme Cordless Drill Driver", "tools", "ACME", "80", 4.0, 10);

        RelatedProduct related = scorer.score(CURRENT, candidate, 4.5).toRelatedProduct();

        assertThat(related.reasons()).containsExactly(
                RelatedReason.LOWER_PRICE, RelatedReason.SAME_BRAND, RelatedReason.SIMILAR_NAME,
                RelatedReason.HIGHER_RATED, RelatedReason.SAME_CATEGORY);
        assertThat(related.primaryReason()).isEqualTo(RelatedReason.LOWER_PRICE);
    }

    @Test
    void scoreAddsEveryComponent() {
        // category 3.0 + brand 2.0 + name 2.0 * 3/5 shared tokens + price 1.5 * (1 - 20/100) + rating 4/5
        ProductView candidate = view(2L, "Acme Cordless Drill Driver", "tools", "ACME", "80", 4.0, 10);

        RelatedProduct related = scorer.score(CURRENT, candidate, null).toRelatedProduct();

        assertThat(related.score()).isEqualTo(8.2);
    }

    @Test
    void scoreIsRoundedToThreeDecimalsInThePayload() {
        // name: 1 shared token of 6 → 2.0 / 6; price 1.5 * (1 - 0.333333 / 100) ≈ 1.495
        ProductView candidate = view(2L, "Drill Bits Assorted", "garden", null, "100.333333", 0, 0);

        RelatedProductScorer.Scored scored = scorer.score(CURRENT, candidate, null);

        assertThat(scored.score()).isCloseTo(2.0 / 6 + 1.5 * (1 - 0.333333 / 100), within(1e-9));
        assertThat(scored.toRelatedProduct().score()).isEqualTo(1.828);
    }

    @Test
    void pricePointsAreZeroAtTwiceThePriceOrMore() {
        ProductView doublePrice = view(2L, "Hose", "garden", null, "200", 0, 0);
        ProductView triplePrice = view(3L, "Hose", "garden", null, "300", 0, 0);

        assertThat(scorer.score(CURRENT, doublePrice, null).score()).isZero();
        assertThat(scorer.score(CURRENT, triplePrice, null).score()).isZero();
    }

    @Test
    void ratingPointsOnlyCountWhenTheCandidateHasReviews() {
        ProductView unreviewed = view(2L, "Hose", "garden", null, "1000", 5.0, 0);
        ProductView reviewed = view(3L, "Hose", "garden", null, "1000", 5.0, 2);

        assertThat(scorer.score(CURRENT, unreviewed, null).score()).isZero();
        assertThat(scorer.score(CURRENT, reviewed, null).score()).isEqualTo(1.0);
    }

    @Test
    void orderIsScoreDescThenReviewCountDescThenIdAsc() {
        List<RelatedProductScorer.Scored> scored = new ArrayList<>(List.of(
                new RelatedProductScorer.Scored(view(5L, "a", "tools", null, "1", 0, 1), 3.0, List.of()),
                new RelatedProductScorer.Scored(view(4L, "b", "tools", null, "1", 0, 1), 3.0, List.of()),
                new RelatedProductScorer.Scored(view(3L, "c", "tools", null, "1", 0, 9), 3.0, List.of()),
                new RelatedProductScorer.Scored(view(9L, "d", "tools", null, "1", 0, 0), 7.5, List.of())));

        scored.sort(RelatedProductScorer.ORDER);

        assertThat(scored).extracting(s -> s.product().id()).containsExactly(9L, 3L, 4L, 5L);
    }

    @Test
    void nameTokensDropShortWordsStopwordsAndNumbers() {
        assertThat(RelatedProductScorer.nameTokens("The 3-Pack USB-C Cable, 6 ft, for Women"))
                .containsExactly("usb", "cable");
    }
}
