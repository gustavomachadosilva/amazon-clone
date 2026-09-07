package com.mercatto.sellers.service;

import com.mercatto.catalog.service.ProductService;
import com.mercatto.orders.domain.Order;
import com.mercatto.orders.domain.OrderItem;
import com.mercatto.orders.domain.OrderStatus;
import com.mercatto.orders.service.OrderService;
import com.mercatto.sellers.service.SellerDashboardService.SellerOrderItemView;
import com.mercatto.sellers.service.SellerDashboardService.SellerOrderView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
        Order order = Order.builder().id(100L).buyerId(20L).status(OrderStatus.PAID).build();
        order.addItem(OrderItem.builder().productId(1L).sellerId(10L).quantity(3).unitPrice(BigDecimal.TEN).build());
        when(orderService.findBySellerId(10L)).thenReturn(List.of(order));

        List<SellerOrderView> result = sellerDashboardService.getReceivedOrders(10L);

        assertThat(result).containsExactly(new SellerOrderView(100L, 20L, OrderStatus.PAID, order.getCreatedAt(),
                List.of(new SellerOrderItemView(1L, 3, BigDecimal.TEN)), BigDecimal.valueOf(30)));
        verify(orderService).findBySellerId(10L);
    }

    @Test
    void getReceivedOrdersOnlyIncludesItemsBelongingToTheSeller() {
        Order order = Order.builder().id(100L).buyerId(20L).status(OrderStatus.PAID).build();
        order.addItem(OrderItem.builder().productId(1L).sellerId(10L).quantity(2).unitPrice(BigDecimal.TEN).build());
        order.addItem(OrderItem.builder().productId(99L).sellerId(77L).quantity(5).unitPrice(BigDecimal.valueOf(50)).build());
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
        Order order = Order.builder().id(100L).buyerId(20L).status(OrderStatus.PAID).build();
        order.addItem(OrderItem.builder().productId(1L).sellerId(10L).quantity(1).unitPrice(BigDecimal.TEN).build());
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
}
