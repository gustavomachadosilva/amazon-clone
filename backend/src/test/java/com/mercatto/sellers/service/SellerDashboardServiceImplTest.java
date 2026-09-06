package com.mercatto.sellers.service;

import com.mercatto.catalog.service.ProductService;
import com.mercatto.orders.domain.Order;
import com.mercatto.orders.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    void getReceivedOrdersComposesProductIdsThenOrders() {
        List<Long> productIds = List.of(1L, 2L);
        List<Order> orders = List.of(Order.builder().id(100L).build());
        when(productService.findProductIdsBySeller(10L)).thenReturn(productIds);
        when(orderService.findByProductIds(productIds)).thenReturn(orders);

        List<Order> result = sellerDashboardService.getReceivedOrders(10L);

        assertThat(result).isEqualTo(orders);
        verify(orderService).findByProductIds(productIds);
    }

    @Test
    void getReceivedOrdersReturnsEmptyWithoutCallingOrderServiceWhenSellerHasNoProducts() {
        when(productService.findProductIdsBySeller(10L)).thenReturn(List.of());

        List<Order> result = sellerDashboardService.getReceivedOrders(10L);

        assertThat(result).isEmpty();
        verifyNoInteractions(orderService);
    }
}
