package com.mercatto.orders.service;

import com.mercatto.orders.service.OrderService.CoPurchase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CoPurchaseScorerTest {

    private final CoPurchaseScorer scorer = new CoPurchaseScorer();

    @Test
    void scoreIsCoBuyersOverTheSquareRootOfPopularity() {
        assertThat(CoPurchaseScorer.score(3, 3)).isCloseTo(Math.sqrt(3), within(1e-9));
        assertThat(CoPurchaseScorer.score(2, 4)).isCloseTo(1.0, within(1e-9));
        assertThat(CoPurchaseScorer.score(2, 0)).isZero();
    }

    @Test
    void normalizationDemotesABestSellerBoughtWithEverything() {
        // Best-seller 10: bought with the anchor by 4 buyers, but by 100 buyers overall.
        // Niche 20: bought with the anchor by 3 buyers, and only by those 3.
        List<CoPurchase> ranked = scorer.rank(1L, Map.of(10L, 4, 20L, 3), Map.of(10L, 100, 20L, 3), 5);

        assertThat(ranked).extracting(CoPurchase::productId).containsExactly(20L, 10L);
        assertThat(ranked.get(0).buyers()).isEqualTo(3);
        assertThat(ranked.get(0).score()).isCloseTo(Math.sqrt(3), within(1e-9));
    }

    @Test
    void pairsBelowTheMinimumSupportAndTheAnchorItselfAreDropped() {
        List<CoPurchase> ranked = scorer.rank(1L, Map.of(1L, 9, 10L, 1, 20L, CoPurchaseScorer.MIN_SUPPORT),
                Map.of(10L, 1, 20L, 2), 5);

        assertThat(ranked).extracting(CoPurchase::productId).containsExactly(20L);
    }

    @Test
    void tiesGoToMoreBuyersThenToTheLowestId() {
        // 30 and 40: same score and same buyers, so the lowest id wins. 50 has the same score
        // (4 / sqrt(16) = 2 / sqrt(4)) with more buyers, so it goes first.
        List<CoPurchase> ranked = scorer.rank(1L, Map.of(40L, 2, 30L, 2, 50L, 4),
                Map.of(40L, 4, 30L, 4, 50L, 16), 5);

        assertThat(ranked).extracting(CoPurchase::productId).containsExactly(50L, 30L, 40L);
    }

    @Test
    void resultIsCappedAtTheLimit() {
        List<CoPurchase> ranked = scorer.rank(1L, Map.of(10L, 5, 20L, 4, 30L, 3), Map.of(), 2);

        assertThat(ranked).extracting(CoPurchase::productId).containsExactly(10L, 20L);
    }

    @Test
    void missingPopularityFallsBackToTheCoPurchaseCount() {
        List<CoPurchase> ranked = scorer.rank(1L, Map.of(10L, 4), Map.of(), 5);

        assertThat(ranked.get(0).score()).isCloseTo(2.0, within(1e-9));
    }
}
