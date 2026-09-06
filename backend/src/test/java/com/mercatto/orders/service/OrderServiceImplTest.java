package com.mercatto.orders.service;

import com.mercatto.catalog.domain.Product;
import com.mercatto.catalog.service.ProductService;
import com.mercatto.orders.domain.Order;
import com.mercatto.orders.domain.OrderStatus;
import com.mercatto.orders.event.OrderPlacedEvent;
import com.mercatto.orders.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductService productService;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OrderServiceImpl orderService;

    @Test
    void checkoutRejectsInsufficientStockWithoutCharging() {
        Product product = Product.builder().id(1L).price(BigDecimal.TEN).stockQuantity(1).build();
        when(productService.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> orderService.checkout(10L, List.of(new OrderService.CheckoutItem(1L, 2))))
                .isInstanceOf(InsufficientStockException.class);

        verifyNoInteractions(paymentGateway);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void checkoutRejectsInsufficientStockWhenSameProductSpansMultipleLines() {
        Product product = Product.builder().id(1L).price(BigDecimal.TEN).stockQuantity(5).build();
        when(productService.findById(1L)).thenReturn(Optional.of(product));

        List<OrderService.CheckoutItem> items = List.of(
                new OrderService.CheckoutItem(1L, 3),
                new OrderService.CheckoutItem(1L, 3));

        assertThatThrownBy(() -> orderService.checkout(10L, items))
                .isInstanceOf(InsufficientStockException.class);

        verifyNoInteractions(paymentGateway);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void checkoutChargesAndSavesWhenStockIsSufficient() {
        Product product = Product.builder().id(1L).price(BigDecimal.TEN).stockQuantity(5).build();
        when(productService.findById(1L)).thenReturn(Optional.of(product));
        when(paymentGateway.charge(any(), any(), any()))
                .thenReturn(new PaymentGateway.PaymentResult(true, "tx-1", "ok"));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order result = orderService.checkout(10L, List.of(new OrderService.CheckoutItem(1L, 2)));

        assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(paymentGateway).charge(any(), any(), any());
        verify(eventPublisher).publishEvent(any(OrderPlacedEvent.class));
    }

    @Test
    void findByProductIdsReturnsEmptyListWithoutInteractingWithRepositoryWhenProductIdsIsEmpty() {
        List<Order> result = orderService.findByProductIds(List.of());

        assertThat(result).isEmpty();
        verifyNoInteractions(orderRepository);
    }

    @Test
    void findByProductIdsReturnsEmptyListWithoutFetchingOrdersWhenNoOrderIdsMatch() {
        when(orderRepository.findOrderIdsByItemsProductIdIn(List.of(1L))).thenReturn(List.of());

        List<Order> result = orderService.findByProductIds(List.of(1L));

        assertThat(result).isEmpty();
        verify(orderRepository, never()).findByIdInWithItems(any());
    }

    @Test
    void findByProductIdsChainsOrderIdLookupThenFetchesOrdersWithItems() {
        Order order = Order.builder().id(5L).build();
        when(orderRepository.findOrderIdsByItemsProductIdIn(List.of(1L, 2L))).thenReturn(List.of(5L));
        when(orderRepository.findByIdInWithItems(List.of(5L))).thenReturn(List.of(order));

        List<Order> result = orderService.findByProductIds(List.of(1L, 2L));

        assertThat(result).containsExactly(order);
    }
}
