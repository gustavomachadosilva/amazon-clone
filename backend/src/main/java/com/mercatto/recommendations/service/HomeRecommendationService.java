package com.mercatto.recommendations.service;

import com.mercatto.catalog.service.ProductService;

import java.util.List;

/**
 * The Home's product shelf (Card #225): "Recommended for you" for a buyer with history, "Top rated"
 * for everyone else.
 *
 * <p><b>Why a module of its own.</b> The shelf combines signals owned by four modules — purchases
 * (Orders), cart lines (Cart), wish lists (Lists) and product details (Catalog). None of them may
 * host it without new dependencies: Catalog must not depend on Orders or Cart, and Orders
 * depending on Cart and Lists would tangle modules that have nothing to do with each other. So
 * {@code recommendations} is a composition module like {@code sellers}: no schema, no entities, no
 * repository, no writes, and no {@code @Transactional} — it only reads the other modules through
 * their public {@code service} interfaces, and nothing depends on it.
 *
 * <p><b>Layers.</b> With purchase, cart or list history, candidates are the products bought
 * together with the buyer's most recent purchases (Card #224) and the best-rated products of the
 * categories the buyer shows most interest in; the layer is {@link HomeLayer#PERSONALIZED} when at
 * least a few of them survive the filters, padded with the fallback when short. Otherwise — and
 * always for anonymous visitors — the layer is {@link HomeLayer#TOP_RATED}: best-rated and
 * best-selling products with at most two per category. Never returned: out-of-stock products and
 * anything the buyer already bought (any PAID order), has in the cart or in a list. The response
 * carries no display text; the storefront names the section from {@link HomeLayer}.
 */
public interface HomeRecommendationService {

    enum HomeLayer {
        /** Built from the buyer's own history ("Recommended for you"). */
        PERSONALIZED,
        /** Not enough history, or anonymous: best rated and best selling ("Top rated"). */
        TOP_RATED
    }

    /** Why an item is on the shelf. */
    enum RecommendationReason {
        /** Other buyers bought it together with something the buyer bought (Card #224). */
        BOUGHT_TOGETHER,
        /** Among the best rated of a category the buyer bought, carted or listed. */
        CATEGORY_AFFINITY,
        /** Among the best rated of its category. */
        TOP_RATED,
        /** Among the products most buyers bought. */
        BEST_SELLER
    }

    record Item(ProductService.ProductView product, RecommendationReason reason) {}

    record HomeRecommendations(HomeLayer layer, List<Item> items) {}

    /**
     * Up to {@code limit} in-stock products for the Home, best first: the personalized ones (if
     * any) before the fallback ones. {@code items} may be shorter than {@code limit}, or empty on
     * an empty catalog.
     *
     * @param userId the signed-in user, or {@code null} for an anonymous visitor (who gets the
     *               {@link HomeLayer#TOP_RATED} layer without any per-user lookup)
     */
    HomeRecommendations forHome(Long userId, int limit);
}
