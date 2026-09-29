package com.mercatto.orders.service;

import com.mercatto.orders.service.OrderService.CoPurchase;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Ranks the products bought together with another one (Card #224). Pure and deterministic — no
 * Spring, no repository — so every rule is unit testable in isolation, like catalog's
 * {@code RelatedProductScorer}.
 *
 * <p>{@code co(A, B)} is the number of distinct buyers with a PAID order containing both A and B;
 * {@code pop(B)} the number of distinct buyers with a PAID order containing B. The score is
 * {@code co / sqrt(pop(B))}: a pair bought together by many buyers ranks high, but a best-seller
 * that simply shows up in everyone's cart is demoted instead of being recommended next to every
 * product. Pairs bought together by fewer than {@link #MIN_SUPPORT} buyers are ignored — one buyer
 * is an anecdote, not a pattern (and would expose that buyer's cart).
 */
final class CoPurchaseScorer {

    static final int MIN_SUPPORT = 2;

    /** Score desc, then more buyers first, then lowest product id for a stable result. */
    static final Comparator<CoPurchase> ORDER = Comparator
            .comparingDouble(CoPurchase::score).reversed()
            .thenComparing(Comparator.comparingInt(CoPurchase::buyers).reversed())
            .thenComparing(CoPurchase::productId);

    /**
     * @param coBuyers   candidate product id → distinct buyers who bought it with the anchor
     * @param popularity product id → distinct buyers who bought it at all; a missing entry falls
     *                   back to the co-purchase count (the product is at least that popular)
     */
    List<CoPurchase> rank(Long anchorId, Map<Long, Integer> coBuyers, Map<Long, Integer> popularity, int limit) {
        return coBuyers.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(anchorId))
                .filter(entry -> entry.getValue() >= MIN_SUPPORT)
                .map(entry -> {
                    int co = entry.getValue();
                    int pop = Math.max(co, popularity.getOrDefault(entry.getKey(), co));
                    return new CoPurchase(entry.getKey(), co, score(co, pop));
                })
                .sorted(ORDER)
                .limit(limit)
                .toList();
    }

    static double score(int coBuyers, int popularity) {
        return popularity <= 0 ? 0.0 : coBuyers / Math.sqrt(popularity);
    }
}
