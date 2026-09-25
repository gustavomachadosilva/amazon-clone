package com.mercatto.sellers.service;

import com.mercatto.catalog.service.ProductService;
import com.mercatto.orders.service.FulfillmentStatus;
import com.mercatto.orders.service.InvalidFulfillmentTransitionException;
import com.mercatto.orders.service.OrderAccessDeniedException;
import com.mercatto.orders.service.OrderService;
import com.mercatto.orders.service.OrderStatus;
import com.mercatto.sellers.service.SellerDashboardService.SellerOrderItemView;
import com.mercatto.sellers.service.SellerDashboardService.SellerOrderView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SellerDashboardServiceImplTest {

    @Mock
    private ProductService productService;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private SellerDashboardServiceImpl sellerDashboardService;

    @Test
    void getReceivedOrdersDelegatesToOrderServiceBySellerId() {
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        OrderService.OrderView order = new OrderService.OrderView(100L, 20L, OrderStatus.PAID, FulfillmentStatus.NOT_SHIPPED, createdAt,
                List.of(new OrderService.OrderItemView(1L, 10L, 3, BigDecimal.TEN)));
        when(orderService.findBySellerId(10L)).thenReturn(List.of(order));

        List<SellerOrderView> result = sellerDashboardService.getReceivedOrders(10L);

        assertThat(result).containsExactly(new SellerOrderView(100L, 20L, OrderStatus.PAID, FulfillmentStatus.NOT_SHIPPED, createdAt,
                List.of(new SellerOrderItemView(1L, 3, BigDecimal.TEN)), BigDecimal.valueOf(30)));
        verify(orderService).findBySellerId(10L);
    }

    @Test
    void getReceivedOrdersOnlyIncludesItemsBelongingToTheSeller() {
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        OrderService.OrderView order = new OrderService.OrderView(100L, 20L, OrderStatus.PAID, FulfillmentStatus.NOT_SHIPPED, createdAt,
                List.of(
                        new OrderService.OrderItemView(1L, 10L, 2, BigDecimal.TEN),
                        new OrderService.OrderItemView(99L, 77L, 5, BigDecimal.valueOf(50))));
        when(orderService.findBySellerId(10L)).thenReturn(List.of(order));

        List<SellerOrderView> result = sellerDashboardService.getReceivedOrders(10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).items()).containsExactly(new SellerOrderItemView(1L, 2, BigDecimal.TEN));
        assertThat(result.get(0).subtotal()).isEqualByComparingTo(BigDecimal.valueOf(20));
    }

    @Test
    void getReceivedOrdersSurvivesProductBeingDeletedFromCatalog() {
        // Order placed before the product was later deleted from the catalog: sellerId is
        // denormalized onto the order item at checkout time, so the order still shows up.
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        OrderService.OrderView order = new OrderService.OrderView(100L, 20L, OrderStatus.PAID, FulfillmentStatus.NOT_SHIPPED, createdAt,
                List.of(new OrderService.OrderItemView(1L, 10L, 1, BigDecimal.TEN)));
        when(orderService.findBySellerId(10L)).thenReturn(List.of(order));

        List<SellerOrderView> result = sellerDashboardService.getReceivedOrders(10L);

        assertThat(result).hasSize(1);
    }

    @Test
    void getReceivedOrdersReturnsEmptyWhenSellerHasNoOrders() {
        when(orderService.findBySellerId(10L)).thenReturn(List.of());

        List<SellerOrderView> result = sellerDashboardService.getReceivedOrders(10L);

        assertThat(result).isEmpty();
    }

    @Test
    void getReceivedOrdersCarriesTheFulfillmentStatus() {
        OrderService.OrderView order = new OrderService.OrderView(100L, 20L, OrderStatus.PAID, FulfillmentStatus.OUT_FOR_DELIVERY,
                Instant.parse("2026-01-01T00:00:00Z"), List.of(new OrderService.OrderItemView(1L, 10L, 1, BigDecimal.TEN)));
        when(orderService.findBySellerId(10L)).thenReturn(List.of(order));

        List<SellerOrderView> result = sellerDashboardService.getReceivedOrders(10L);

        assertThat(result.get(0).fulfillmentStatus()).isEqualTo(FulfillmentStatus.OUT_FOR_DELIVERY);
    }

    @Test
    void advanceFulfillmentDelegatesToOrderServiceWithOrderIdFirst() {
        OrderService.OrderView order = new OrderService.OrderView(100L, 20L, OrderStatus.PAID, FulfillmentStatus.SHIPPED,
                Instant.parse("2026-01-01T00:00:00Z"), List.of(new OrderService.OrderItemView(1L, 10L, 1, BigDecimal.TEN)));
        when(orderService.advanceFulfillment(100L, 10L, FulfillmentStatus.SHIPPED)).thenReturn(order);

        sellerDashboardService.advanceFulfillment(10L, 100L, FulfillmentStatus.SHIPPED);

        verify(orderService).advanceFulfillment(100L, 10L, FulfillmentStatus.SHIPPED);
    }

    @Test
    void advanceFulfillmentReturnsOnlyTheSellersOwnItems() {
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        OrderService.OrderView order = new OrderService.OrderView(100L, 20L, OrderStatus.PAID, FulfillmentStatus.SHIPPED, createdAt,
                List.of(
                        new OrderService.OrderItemView(1L, 10L, 2, BigDecimal.TEN),
                        new OrderService.OrderItemView(99L, 77L, 5, BigDecimal.valueOf(50))));
        when(orderService.advanceFulfillment(100L, 10L, FulfillmentStatus.SHIPPED)).thenReturn(order);

        SellerOrderView result = sellerDashboardService.advanceFulfillment(10L, 100L, FulfillmentStatus.SHIPPED);

        assertThat(result.fulfillmentStatus()).isEqualTo(FulfillmentStatus.SHIPPED);
        assertThat(result.items()).containsExactly(new SellerOrderItemView(1L, 2, BigDecimal.TEN));
        assertThat(result.subtotal()).isEqualByComparingTo(BigDecimal.valueOf(20));
    }

    @Test
    void advanceFulfillmentPropagatesAccessDenied() {
        when(orderService.advanceFulfillment(100L, 10L, FulfillmentStatus.SHIPPED))
                .thenThrow(new OrderAccessDeniedException("Seller has no items in this order"));

        assertThatThrownBy(() -> sellerDashboardService.advanceFulfillment(10L, 100L, FulfillmentStatus.SHIPPED))
                .isInstanceOf(OrderAccessDeniedException.class);
    }

    @Test
    void advanceFulfillmentPropagatesInvalidTransition() {
        when(orderService.advanceFulfillment(100L, 10L, FulfillmentStatus.DELIVERED))
                .thenThrow(new InvalidFulfillmentTransitionException("Cannot skip a step"));

        assertThatThrownBy(() -> sellerDashboardService.advanceFulfillment(10L, 100L, FulfillmentStatus.DELIVERED))
                .isInstanceOf(InvalidFulfillmentTransitionException.class);
    }
}
