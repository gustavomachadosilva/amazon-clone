package com.mercatto.integration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Pure ranking/latency metrics for the search evaluation (#219). No I/O, no Spring: unit tested in
 * {@code SearchMetricsTest} and used by {@code SearchEvalIT}. Definitions (also documented in
 * {@code docs/search-recommendation-baseline.md}):
 *
 * <ul>
 *   <li><b>precision@k</b>: relevant results among the first {@code k} shown, divided by the number
 *       of results actually shown ({@code min(k, results)}); 0 when nothing is shown. Dividing by
 *       what is shown (not by {@code k}) keeps a query with only 1–3 relevant products from being
 *       capped at 0.1–0.3 even when it is answered perfectly.</li>
 *   <li><b>recall@k</b>: relevant results among the first {@code k}, divided by
 *       {@code min(k, |relevant|)} — so a perfect first page scores 1 even when more than {@code k}
 *       products are relevant.</li>
 *   <li><b>reciprocal rank</b>: {@code 1 / position} of the first relevant result within the first
 *       {@code k} (1-based); 0 when none. Its mean over queries is the MRR@k.</li>
 *   <li><b>percentile</b>: nearest-rank method — the value at 1-based rank {@code ceil(p/100 * n)}
 *       of the sorted sample.</li>
 * </ul>
 */
final class SearchMetrics {

    private SearchMetrics() {}

    static <T> int hitsAtK(List<T> ranked, Set<T> relevant, int k) {
        int hits = 0;
        for (int i = 0; i < Math.min(k, ranked.size()); i++) {
            if (relevant.contains(ranked.get(i))) {
                hits++;
            }
        }
        return hits;
    }

    static <T> double precisionAtK(List<T> ranked, Set<T> relevant, int k) {
        requirePositive(k);
        int shown = Math.min(k, ranked.size());
        return shown == 0 ? 0.0 : (double) hitsAtK(ranked, relevant, k) / shown;
    }

    static <T> double recallAtK(List<T> ranked, Set<T> relevant, int k) {
        requirePositive(k);
        if (relevant.isEmpty()) {
            throw new IllegalArgumentException("recall is undefined for an empty relevant set");
        }
        return (double) hitsAtK(ranked, relevant, k) / Math.min(k, relevant.size());
    }

    static <T> double reciprocalRank(List<T> ranked, Set<T> relevant, int k) {
        requirePositive(k);
        for (int i = 0; i < Math.min(k, ranked.size()); i++) {
            if (relevant.contains(ranked.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    /** Fraction of {@code resultCounts} that are zero; 0 for an empty input. */
    static double zeroResultRate(Collection<Long> resultCounts) {
        if (resultCounts.isEmpty()) {
            return 0.0;
        }
        long zeros = resultCounts.stream().filter(count -> count == 0).count();
        return (double) zeros / resultCounts.size();
    }

    static double mean(Collection<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    /** Nearest-rank percentile ({@code 0 < p <= 100}) of a non-empty sample. */
    static long percentile(Collection<Long> sample, double p) {
        if (sample.isEmpty()) {
            throw new IllegalArgumentException("percentile of an empty sample");
        }
        if (p <= 0 || p > 100) {
            throw new IllegalArgumentException("p must be in (0, 100]: " + p);
        }
        List<Long> sorted = new ArrayList<>(sample);
        Collections.sort(sorted);
        int rank = (int) Math.ceil(p * sorted.size() / 100.0);
        return sorted.get(Math.max(rank, 1) - 1);
    }

    private static void requirePositive(int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("k must be positive: " + k);
        }
    }
}
