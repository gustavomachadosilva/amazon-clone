package com.mercatto.recommendations.service;

import com.mercatto.cart.service.CartService;
import com.mercatto.catalog.service.ProductService;
import com.mercatto.catalog.service.ProductService.ProductView;
import com.mercatto.lists.service.WishListService;
import com.mercatto.orders.service.OrderService;
import com.mercatto.recommendations.service.HomeRecommendationService.HomeLayer;
import com.mercatto.recommendations.service.HomeRecommendationService.HomeRecommendations;
import com.mercatto.recommendations.service.HomeRecommendationService.Item;
import com.mercatto.recommendations.service.HomeRecommendationService.RecommendationReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeRecommendationServiceImplTest {

    @Mock
    private ProductService productService;

    @Mock
    private OrderService orderService;

    @Mock
    private CartService cartService;

    @Mock
    private WishListService wishListService;

    @InjectMocks
    private HomeRecommendationServiceImpl service;

    private static ProductView view(long id, String category) {
        return new ProductView(id, "Product " + id, null, BigDecimal.TEN, 10, category, null, null, 12, null, null,
                1L, null, 4.0, 5L);
    }

    private static Collection<Long> containing(Long... ids) {
        return argThat(actual -> actual != null && Set.copyOf(actual).equals(Set.of(ids)));
    }

    private void stubFallback(List<ProductView> topRated) {
        when(productService.findTopRatedInStockPerCategory(HomeRecommendationServiceImpl.FALLBACK_PER_CATEGORY,
                HomeRecommendationServiceImpl.FALLBACK_POOL)).thenReturn(topRated);
        when(orderService.findBestSellers(HomeRecommendationServiceImpl.BEST_SELLER_POOL)).thenReturn(List.of());
        lenient().when(productService.findViewsByIds(List.of())).thenReturn(List.of());
        lenient().when(orderService.countBuyers(any())).thenReturn(Map.of());
    }

    @Test
    void anonymousVisitorsGetTheTopRatedLayerWithoutAnyPerUserLookup() {
        stubFallback(List.of(view(1, "a"), view(2, "b")));

        HomeRecommendations result = service.forHome(null, 12);

        assertThat(result.layer()).isEqualTo(HomeLayer.TOP_RATED);
        assertThat(result.items()).extracting(item -> item.product().id(), Item::reason).containsExactly(
                tuple(1L, RecommendationReason.TOP_RATED), tuple(2L, RecommendationReason.TOP_RATED));
        verifyNoInteractions(cartService, wishListService);
        verify(orderService, never()).findPurchasedProducts(any());
        verify(orderService, never()).coPurchasedWith(anyLong(), anyInt());
        verify(productService, never()).findTopRatedInStock(any(), anyInt());
    }

    @Test
    void aUserWithoutHistoryGetsTheTopRatedLayer() {
        when(orderService.findPurchasedProducts(7L)).thenReturn(List.of());
        when(cartService.findProductIds(7L)).thenReturn(List.of());
        when(wishListService.listByBuyer(7L)).thenReturn(List.of());
        stubFallback(List.of(view(1, "a")));

        HomeRecommendations result = service.forHome(7L, 12);

        assertThat(result.layer()).isEqualTo(HomeLayer.TOP_RATED);
        verify(orderService, never()).coPurchasedWith(anyLong(), anyInt());
        verify(productService, never()).findTopRatedInStock(any(), anyInt());
    }

    @Test
    void mergesCoPurchasesOfTheMostRecentPurchasesWithTheTopCategoriesAndExcludesTheBuyersProducts() {
        Instant now = Instant.parse("2026-09-01T00:00:00Z");
        // Six purchases, newest first: only the five most recent are co-purchase anchors.
        when(orderService.findPurchasedProducts(7L)).thenReturn(List.of(
                new OrderService.PurchasedProduct(1L, now),
                new OrderService.PurchasedProduct(2L, now.minusSeconds(10)),
                new OrderService.PurchasedProduct(3L, now.minusSeconds(20)),
                new OrderService.PurchasedProduct(4L, now.minusSeconds(30)),
                new OrderService.PurchasedProduct(5L, now.minusSeconds(40)),
                new OrderService.PurchasedProduct(6L, now.minusSeconds(50))));
        when(cartService.findProductIds(7L)).thenReturn(List.of(20L));
        when(wishListService.listByBuyer(7L)).thenReturn(List.of(
                new WishListService.WishListView(1L, 7L, "Later", List.of(21L), now)));
        when(productService.findViewsByIds(containing(1L, 2L, 3L, 4L, 5L, 6L, 20L, 21L))).thenReturn(List.of(
                view(1, "games"), view(2, "games"), view(3, "games"), view(4, "games"), view(5, "games"),
                view(6, "games"), view(20, "books"), view(21, "games")));

        when(orderService.coPurchasedWith(anyLong(), anyInt())).thenReturn(List.of());
        // 30 is bought together with two anchors, 31 with one; 2 is an anchor itself (already bought).
        when(orderService.coPurchasedWith(1L, HomeRecommendationServiceImpl.CO_PURCHASE_PER_ANCHOR))
                .thenReturn(List.of(new OrderService.CoPurchase(30L, 2, 1.0), new OrderService.CoPurchase(2L, 3, 2.0)));
        when(orderService.coPurchasedWith(2L, HomeRecommendationServiceImpl.CO_PURCHASE_PER_ANCHOR))
                .thenReturn(List.of(new OrderService.CoPurchase(30L, 2, 1.0), new OrderService.CoPurchase(31L, 2, 1.5)));
        when(productService.findViewsByIds(containing(30L, 31L, 2L)))
                .thenReturn(List.of(view(30, "garden"), view(31, "garden"), view(2, "games")));
        when(productService.findTopRatedInStock("games", HomeRecommendationServiceImpl.CATEGORY_POOL))
                .thenReturn(List.of(view(3, "games"), view(40, "games")));
        when(productService.findTopRatedInStock("books", HomeRecommendationServiceImpl.CATEGORY_POOL))
                .thenReturn(List.of(view(20, "books"), view(50, "books")));
        stubFallback(List.of(view(60, "tools")));

        HomeRecommendations result = service.forHome(7L, 12);

        assertThat(result.layer()).isEqualTo(HomeLayer.PERSONALIZED);
        assertThat(result.items()).extracting(item -> item.product().id(), Item::reason).containsExactly(
                tuple(30L, RecommendationReason.BOUGHT_TOGETHER),
                tuple(31L, RecommendationReason.BOUGHT_TOGETHER),
                tuple(40L, RecommendationReason.CATEGORY_AFFINITY),
                tuple(50L, RecommendationReason.CATEGORY_AFFINITY),
                tuple(60L, RecommendationReason.TOP_RATED));
        verify(orderService, never()).coPurchasedWith(6L, HomeRecommendationServiceImpl.CO_PURCHASE_PER_ANCHOR);
        verify(orderService).countBuyers(containing(30L, 31L, 2L, 3L, 40L, 20L, 50L, 60L));
    }
}
