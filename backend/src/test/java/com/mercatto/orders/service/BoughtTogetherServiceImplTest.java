package com.mercatto.orders.service;

import com.mercatto.catalog.service.ProductNotFoundException;
import com.mercatto.catalog.service.ProductService;
import com.mercatto.catalog.service.ProductService.ProductView;
import com.mercatto.catalog.service.RelatedReason;
import com.mercatto.orders.service.BoughtTogetherService.BoughtTogether;
import com.mercatto.orders.service.BoughtTogetherService.BoughtTogetherSource;
import com.mercatto.orders.service.BoughtTogetherService.Item;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoughtTogetherServiceImplTest {

    @Mock
    private OrderService orderService;

    @Mock
    private ProductService productService;

    @InjectMocks
    private BoughtTogetherServiceImpl boughtTogetherService;

    private static ProductView view(long id, int stock) {
        return new ProductView(id, "Product " + id, null, BigDecimal.TEN, stock, "Games", null, null, 12, null,
                null, 20L, null, 0.0, 0L);
    }

    private static ProductService.ProductSummary summary(long id) {
        return new ProductService.ProductSummary(id, "Product " + id, null, BigDecimal.TEN, 5, "Games", null, null,
                12, null, null, 20L, null);
    }

    private void productExists(long id) {
        when(productService.findById(id)).thenReturn(Optional.of(summary(id)));
    }

    @Test
    void returnsCoPurchasesInTheirRankingOrderWithTheBuyerCount() {
        productExists(1L);
        when(orderService.coPurchasedWith(1L, 2 * BoughtTogetherServiceImpl.CANDIDATE_MULTIPLIER)).thenReturn(List.of(
                new OrderService.CoPurchase(30L, 3, 1.73), new OrderService.CoPurchase(10L, 2, 1.41)));
        // Catalog doesn't guarantee the order: the ranking must still win.
        when(productService.findViewsByIds(List.of(30L, 10L))).thenReturn(List.of(view(10L, 5), view(30L, 5)));

        BoughtTogether result = boughtTogetherService.find(1L, 2);

        assertThat(result.source()).isEqualTo(BoughtTogetherSource.CO_PURCHASE);
        assertThat(result.items())
                .extracting(item -> item.product().id(), Item::timesBoughtTogether, Item::primaryReason)
                .containsExactly(tuple(30L, 3, null), tuple(10L, 2, null));
        verify(productService, never()).findRelated(anyLong(), anyInt());
    }

    @Test
    void dropsDeletedAndOutOfStockProductsAndKeepsTheLimit() {
        productExists(1L);
        when(orderService.coPurchasedWith(1L, 2 * BoughtTogetherServiceImpl.CANDIDATE_MULTIPLIER)).thenReturn(List.of(
                new OrderService.CoPurchase(10L, 5, 2.0),   // deleted from the catalog
                new OrderService.CoPurchase(20L, 4, 1.9),   // out of stock
                new OrderService.CoPurchase(30L, 3, 1.8),
                new OrderService.CoPurchase(40L, 3, 1.7),
                new OrderService.CoPurchase(50L, 2, 1.6)));
        when(productService.findViewsByIds(List.of(10L, 20L, 30L, 40L, 50L)))
                .thenReturn(List.of(view(20L, 0), view(30L, 1), view(40L, 8), view(50L, 8)));

        BoughtTogether result = boughtTogetherService.find(1L, 2);

        assertThat(result.source()).isEqualTo(BoughtTogetherSource.CO_PURCHASE);
        assertThat(result.items()).extracting(item -> item.product().id()).containsExactly(30L, 40L);
    }

    @Test
    void fallsBackToSimilarProductsWhenNoCoPurchaseSurvives() {
        productExists(1L);
        when(orderService.coPurchasedWith(1L, 6)).thenReturn(List.of(new OrderService.CoPurchase(10L, 2, 1.41)));
        when(productService.findViewsByIds(List.of(10L))).thenReturn(List.of(view(10L, 0)));
        when(productService.findRelated(1L, 2)).thenReturn(List.of(new ProductService.RelatedProduct(
                view(99L, 5), 6.5, RelatedReason.LOWER_PRICE, List.of(RelatedReason.LOWER_PRICE))));

        BoughtTogether result = boughtTogetherService.find(1L, 2);

        assertThat(result.source()).isEqualTo(BoughtTogetherSource.SIMILAR);
        assertThat(result.items())
                .extracting(item -> item.product().id(), Item::timesBoughtTogether, Item::primaryReason)
                .containsExactly(tuple(99L, null, RelatedReason.LOWER_PRICE));
    }

    @Test
    void fallsBackToSimilarProductsWithoutPurchaseHistory() {
        productExists(1L);
        when(orderService.coPurchasedWith(1L, 6)).thenReturn(List.of());
        when(productService.findRelated(1L, 2)).thenReturn(List.of());

        BoughtTogether result = boughtTogetherService.find(1L, 2);

        assertThat(result.source()).isEqualTo(BoughtTogetherSource.SIMILAR);
        assertThat(result.items()).isEmpty();
        verify(productService, never()).findViewsByIds(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unknownProductThrows() {
        when(productService.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> boughtTogetherService.find(99L, 2)).isInstanceOf(ProductNotFoundException.class);

        verifyNoInteractions(orderService);
    }
}
