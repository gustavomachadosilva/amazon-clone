package com.mercatto.integration;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class SearchMetricsTest {

    private static final double EPS = 1e-9;

    @Test
    void precisionDividesByTheResultsActuallyShown() {
        assertThat(SearchMetrics.precisionAtK(List.of(1, 2, 3, 4), Set.of(2, 4, 9), 10)).isCloseTo(0.5, within(EPS));
        assertThat(SearchMetrics.precisionAtK(List.of(1, 2, 3), Set.of(1), 2)).isCloseTo(0.5, within(EPS));
    }

    @Test
    void precisionOfAnEmptyResultIsZero() {
        assertThat(SearchMetrics.precisionAtK(List.<Integer>of(), Set.of(1), 10)).isZero();
    }

    @Test
    void precisionOnlyLooksAtTheFirstK() {
        assertThat(SearchMetrics.precisionAtK(List.of(9, 9, 1), Set.of(1), 2)).isZero();
    }

    @Test
    void recallIsCappedAtK() {
        // 12 relevant, first page of 10 holds 5 of them -> 5 / min(10, 12)
        Set<Integer> relevant = Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        List<Integer> ranked = List.of(1, 2, 3, 4, 5, 100, 101, 102, 103, 104);
        assertThat(SearchMetrics.recallAtK(ranked, relevant, 10)).isCloseTo(0.5, within(EPS));
        // 2 relevant, both found -> 1.0
        assertThat(SearchMetrics.recallAtK(List.of(7, 1, 2), Set.of(1, 2), 10)).isCloseTo(1.0, within(EPS));
    }

    @Test
    void recallRejectsAnEmptyRelevantSet() {
        assertThatThrownBy(() -> SearchMetrics.recallAtK(List.of(1), Set.of(), 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reciprocalRankUsesTheFirstRelevantPosition() {
        assertThat(SearchMetrics.reciprocalRank(List.of(5, 6, 1, 2), Set.of(1, 2), 10)).isCloseTo(1.0 / 3, within(EPS));
        assertThat(SearchMetrics.reciprocalRank(List.of(1), Set.of(1), 10)).isCloseTo(1.0, within(EPS));
        assertThat(SearchMetrics.reciprocalRank(List.of(5, 6), Set.of(1), 10)).isZero();
        assertThat(SearchMetrics.reciprocalRank(List.of(5, 6, 1), Set.of(1), 2)).isZero();
    }

    @Test
    void zeroResultRateCountsEmptyResults() {
        assertThat(SearchMetrics.zeroResultRate(List.of(0L, 3L, 0L, 10L))).isCloseTo(0.5, within(EPS));
        assertThat(SearchMetrics.zeroResultRate(List.of())).isZero();
    }

    @Test
    void percentileUsesNearestRank() {
        List<Long> sample = List.of(15L, 20L, 35L, 40L, 50L);
        assertThat(SearchMetrics.percentile(sample, 5)).isEqualTo(15L);
        assertThat(SearchMetrics.percentile(sample, 30)).isEqualTo(20L);
        assertThat(SearchMetrics.percentile(sample, 40)).isEqualTo(20L);
        assertThat(SearchMetrics.percentile(sample, 50)).isEqualTo(35L);
        assertThat(SearchMetrics.percentile(sample, 100)).isEqualTo(50L);
    }

    @Test
    void percentileOfHundredSamplesPicksTheNinetyFifth() {
        List<Long> sample = new java.util.ArrayList<>();
        for (long i = 100; i >= 1; i--) {
            sample.add(i);
        }
        assertThat(SearchMetrics.percentile(sample, 95)).isEqualTo(95L);
        assertThat(SearchMetrics.percentile(sample, 50)).isEqualTo(50L);
    }

    @Test
    void percentileRejectsBadInput() {
        assertThatThrownBy(() -> SearchMetrics.percentile(List.of(), 95)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SearchMetrics.percentile(List.of(1L), 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void meanOfNothingIsZero() {
        assertThat(SearchMetrics.mean(List.of())).isZero();
        assertThat(SearchMetrics.mean(List.of(1.0, 0.0))).isCloseTo(0.5, within(EPS));
    }
}
