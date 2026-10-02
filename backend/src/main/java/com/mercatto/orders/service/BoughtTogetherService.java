package com.mercatto.orders.service;

import com.mercatto.catalog.service.ProductService;
import com.mercatto.catalog.service.RelatedReason;

import java.util.List;

/**
 * The product page's "Frequently bought together" bundle (Card #224), built from purchase history.
 *
 * <p><b>Why it lives in Orders, and why it's a live query.</b> The co-purchase data is Orders'
 * own, and Catalog may not depend on Orders at all ({@code ArchitectureBoundaryTest}: catalog must
 * not depend on orders, and modules must be free of cycles — Orders already depends on
 * {@code catalog.service}). So the composition happens here: Orders counts co-purchases with a
 * query over its own tables ({@link OrderService#coPurchasedWith}) and reads the product details
 * through Catalog's public {@link ProductService}. A projection table fed by
 * {@code OrderPlacedEvent} was considered and rejected for now: there is no cancel/refund event to
 * decrement it with, it would need a backfill of existing orders, and an AFTER_COMMIT listener
 * without an outbox can silently lose increments — while the order volume is small enough for the
 * indexed query to be cheap. Revisit when the query shows up as slow, or when orders get a
 * cancellation/refund flow; a projection can then replace the query behind this same interface.
 *
 * <p><b>Cold start.</b> When no product has enough co-purchase support (see
 * {@link OrderService#coPurchasedWith}) — or none of those products is still sold and in stock —
 * the bundle falls back to Catalog's similar products (Card #223) and says so through
 * {@link BoughtTogetherSource#SIMILAR}, so the storefront never presents a similarity guess as
 * "bought together". The two sources are never mixed in one response.
 */
public interface BoughtTogetherService {

    enum BoughtTogetherSource {
        /** Items really bought together with the product by other buyers (PAID orders only). */
        CO_PURCHASE,
        /** Not enough purchase history: items similar to the product, not bought together. */
        SIMILAR
    }

    /**
     * @param product             the recommended product (in stock when returned)
     * @param timesBoughtTogether distinct buyers who bought it with the current product; null when
     *                            the source is {@link BoughtTogetherSource#SIMILAR}
     * @param primaryReason       why it is similar (Card #223); null when the source is
     *                            {@link BoughtTogetherSource#CO_PURCHASE}
     */
    record Item(ProductService.ProductView product, Integer timesBoughtTogether, RelatedReason primaryReason) {}

    record BoughtTogether(BoughtTogetherSource source, List<Item> items) {}

    /**
     * Up to {@code limit} products to offer together with {@code productId}: the co-purchased ones
     * when there are any in stock, otherwise the similar ones. {@code items} may be empty.
     *
     * @throws com.mercatto.catalog.service.ProductNotFoundException when {@code productId}
     *         doesn't exist
     */
    BoughtTogether find(Long productId, int limit);
}
