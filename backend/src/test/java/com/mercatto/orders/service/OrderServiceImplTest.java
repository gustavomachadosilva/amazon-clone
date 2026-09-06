package com.mercatto.orders.service;

import com.mercatto.catalog.domain.Product;
import com.mercatto.catalog.service.ProductService;
import com.mercatto.orders.domain.Order;
import com.mercatto.orders.domain.OrderItem;
import com.mercatto.orders.domain.OrderStatus;
import com.mercatto.orders.event.OrderPlacedEvent;
import com.mercatto.orders.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
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

        ArgumentCaptor<OrderPlacedEvent> eventCaptor = ArgumentCaptor.forClass(OrderPlacedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        OrderPlacedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.orderId()).isEqualTo(result.getId());
        assertThat(publishedEvent.buyerId()).isEqualTo(10L);
        assertThat(publishedEvent.items())
                .extracting(OrderPlacedEvent.Item::productId, OrderPlacedEvent.Item::quantity)
                .containsExactly(tuple(1L, 2));
    }

    @Test
    void checkoutSucceedsWhenRequestedQuantityEqualsAvailableStock() {
        Product product = Product.builder().id(1L).price(BigDecimal.TEN).stockQuantity(3).build();
        when(productService.findById(1L)).thenReturn(Optional.of(product));
        when(paymentGateway.charge(any(), any(), any()))
                .thenReturn(new PaymentGateway.PaymentResult(true, "tx-1", "ok"));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order result = orderService.checkout(10L, List.of(new OrderService.CheckoutItem(1L, 3)));

        assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(paymentGateway).charge(any(), any(), any());
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void checkoutWithMultipleProductsComputesTotalAndItemsCorrectly() {
        Product product1 = Product.builder().id(1L).price(BigDecimal.valueOf(10)).stockQuantity(5).build();
        Product product2 = Product.builder().id(2L).price(BigDecimal.valueOf(5)).stockQuantity(10).build();
        when(productService.findById(1L)).thenReturn(Optional.of(product1));
        when(productService.findById(2L)).thenReturn(Optional.of(product2));
        when(paymentGateway.charge(any(), any(), any()))
                .thenReturn(new PaymentGateway.PaymentResult(true, "tx-1", "ok"));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<OrderService.CheckoutItem> items = List.of(
                new OrderService.CheckoutItem(1L, 2),
                new OrderService.CheckoutItem(2L, 3));

        Order result = orderService.checkout(10L, items);

        assertThat(result.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(35));
        assertThat(result.getItems())
                .extracting(OrderItem::getProductId, OrderItem::getQuantity, OrderItem::getUnitPrice)
                .containsExactly(
                        tuple(1L, 2, BigDecimal.valueOf(10)),
                        tuple(2L, 3, BigDecimal.valueOf(5)));
    }

    @Test
    void checkoutRejectsWhenOneOfSeveralItemsHasInsufficientStock() {
        Product product1 = Product.builder().id(1L).price(BigDecimal.TEN).stockQuantity(5).build();
        Product product2 = Product.builder().id(2L).price(BigDecimal.valueOf(5)).stockQuantity(1).build();
        when(productService.findById(1L)).thenReturn(Optional.of(product1));
        when(productService.findById(2L)).thenReturn(Optional.of(product2));

        List<OrderService.CheckoutItem> items = List.of(
                new OrderService.CheckoutItem(1L, 2),
                new OrderService.CheckoutItem(2L, 5));

        assertThatThrownBy(() -> orderService.checkout(10L, items))
                .isInstanceOf(InsufficientStockException.class);

        verifyNoInteractions(paymentGateway);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void checkoutThrowsWhenProductNotFound() {
        when(productService.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.checkout(10L, List.of(new OrderService.CheckoutItem(1L, 1))))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(paymentGateway);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void checkoutSavesAsFailedAndDoesNotPublishEventWhenPaymentIsDeclined() {
        Product product = Product.builder().id(1L).price(BigDecimal.TEN).stockQuantity(5).build();
        when(productService.findById(1L)).thenReturn(Optional.of(product));
        when(paymentGateway.charge(any(), any(), any()))
                .thenReturn(new PaymentGateway.PaymentResult(false, null, "declined"));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order result = orderService.checkout(10L, List.of(new OrderService.CheckoutItem(1L, 2)));

        assertThat(result.getStatus()).isEqualTo(OrderStatus.FAILED);
        verify(orderRepository).save(any(Order.class));
        verifyNoInteractions(eventPublisher);
    }
}
