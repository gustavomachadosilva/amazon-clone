package com.mercatto.recommendations.service;

import com.mercatto.cart.service.CartService;
import com.mercatto.catalog.service.ProductService;
import com.mercatto.catalog.service.ProductService.ProductView;
import com.mercatto.lists.service.WishListService;
import com.mercatto.orders.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gathers the Home's candidates from Orders, Cart, Lists and Catalog — only through their public
 * service interfaces — and lets {@link HomeRecommendationRanker} pick the shelf. Read-only and
 * deliberately not {@code @Transactional}: each call runs in its own module's (read) transaction,
 * so no transaction spans two modules (see {@link HomeRecommendationService} for why this is a
 * module of its own).
 */
@Service
@RequiredArgsConstructor
class HomeRecommendationServiceImpl implements HomeRecommendationService {

    /** The buyer's most recent purchases whose co-purchases are looked up. */
    static final int ANCHORS = 5;
    static final int CO_PURCHASE_PER_ANCHOR = 10;
    /** Best-rated products read from each of the buyer's top categories. */
    static final int CATEGORY_POOL = 20;
    static final int FALLBACK_PER_CATEGORY = 3;
    static final int FALLBACK_POOL = 120;
    static final int BEST_SELLER_POOL = 50;

    private final ProductService productService;
    private final OrderService orderService;
    private final CartService cartService;
    private final WishListService wishListService;
    private final HomeRecommendationRanker ranker = new HomeRecommendationRanker();

    @Override
    public HomeRecommendations forHome(Long userId, int limit) {
        HomeRecommendationRanker.Profile profile = HomeRecommendationRanker.Profile.EMPTY;
        Map<Long, Double> coPurchaseScores = new HashMap<>();
        List<ProductView> personalizedCandidates = new ArrayList<>();

        // Anonymous visitors skip every per-user lookup.
        if (userId != null) {
            List<Long> purchasedIds = orderService.findPurchasedProducts(userId).stream()
                    .map(OrderService.PurchasedProduct::productId)
                    .toList();
            List<Long> cartIds = cartService.findProductIds(userId);
            List<Long> listIds = wishListService.listByBuyer(userId).stream()
                    .flatMap(list -> list.productIds().stream())
                    .distinct()
                    .toList();
            Set<Long> signalIds = new LinkedHashSet<>(purchasedIds);
            signalIds.addAll(cartIds);
            signalIds.addAll(listIds);

            if (!signalIds.isEmpty()) {
                profile = ranker.profile(purchasedIds, cartIds, listIds, productService.findViewsByIds(signalIds));

                // Newest purchases first, so the anchors are what the buyer bought most recently.
                for (Long anchor : purchasedIds.stream().limit(ANCHORS).toList()) {
                    orderService.coPurchasedWith(anchor, CO_PURCHASE_PER_ANCHOR)
                            .forEach(co -> coPurchaseScores.merge(co.productId(), co.score(), Double::sum));
                }
                personalizedCandidates.addAll(productService.findViewsByIds(coPurchaseScores.keySet()));
                for (String category : profile.topCategories()) {
                    personalizedCandidates.addAll(productService.findTopRatedInStock(category, CATEGORY_POOL));
                }
            }
        }

        List<ProductView> topRated = productService.findTopRatedInStockPerCategory(FALLBACK_PER_CATEGORY, FALLBACK_POOL);
        List<OrderService.ProductPopularity> bestSellerCounts = orderService.findBestSellers(BEST_SELLER_POOL);
        List<ProductView> bestSellers = productService.findViewsByIds(bestSellerCounts.stream()
                .map(OrderService.ProductPopularity::productId)
                .toList());

        // One popularity lookup for every candidate whose buyer count isn't known yet.
        Map<Long, Integer> buyers = new HashMap<>();
        bestSellerCounts.forEach(count -> buyers.put(count.productId(), count.buyers()));
        Set<Long> uncounted = new LinkedHashSet<>();
        personalizedCandidates.forEach(view -> uncounted.add(view.id()));
        topRated.forEach(view -> uncounted.add(view.id()));
        uncounted.removeAll(buyers.keySet());
        if (!uncounted.isEmpty()) {
            buyers.putAll(orderService.countBuyers(uncounted));
        }

        return ranker.select(
                ranker.scorePersonalized(profile, coPurchaseScores, personalizedCandidates, buyers),
                ranker.scoreFallback(profile, topRated, bestSellers, buyers),
                limit);
    }
}
