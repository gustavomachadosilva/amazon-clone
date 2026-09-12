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
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderReservationService orderReservationService;

    @Mock
    private ProductService productService;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OrderServiceImpl orderService;

    private void stubReserveAndUpdateStatus() {
        when(orderReservationService.reserve(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderReservationService.updateStatus(any(Order.class), any(OrderStatus.class)))
                .thenAnswer(invocation -> {
                    Order order = invocation.getArgument(0);
                    order.setStatus(invocation.getArgument(1));
                    return order;
                });
    }

    @Test
    void checkoutRejectsInsufficientStockWithoutCharging() {
        Product product = Product.builder().id(1L).price(BigDecimal.TEN).stockQuantity(1).build();
        when(productService.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> orderService.checkout(10L, List.of(new OrderService.CheckoutItem(1L, 2)), null))
                .isInstanceOf(InsufficientStockException.class);

        verifyNoInteractions(paymentGateway);
        verifyNoInteractions(orderReservationService);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void checkoutRejectsInsufficientStockWhenSameProductSpansMultipleLines() {
        Product product = Product.builder().id(1L).price(BigDecimal.TEN).stockQuantity(5).build();
        when(productService.findById(1L)).thenReturn(Optional.of(product));

        List<OrderService.CheckoutItem> items = List.of(
                new OrderService.CheckoutItem(1L, 3),
                new OrderService.CheckoutItem(1L, 3));

        assertThatThrownBy(() -> orderService.checkout(10L, items, null))
                .isInstanceOf(InsufficientStockException.class);

        verifyNoInteractions(paymentGateway);
        verifyNoInteractions(orderReservationService);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void checkoutChargesAndSavesWhenStockIsSufficient() {
        Product product = Product.builder().id(1L).price(BigDecimal.TEN).stockQuantity(5).build();
        when(productService.findById(1L)).thenReturn(Optional.of(product));
        when(paymentGateway.charge(any(), any(), any()))
                .thenReturn(new PaymentGateway.PaymentResult(true, "tx-1", "ok"));
        stubReserveAndUpdateStatus();

        Order result = orderService.checkout(10L, List.of(new OrderService.CheckoutItem(1L, 2)), null);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(paymentGateway).charge(any(), any(), any());
        verify(orderReservationService).reserve(any(Order.class));
        verify(orderReservationService).updateStatus(any(Order.class), eq(OrderStatus.PAID));

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
        stubReserveAndUpdateStatus();

        Order result = orderService.checkout(10L, List.of(new OrderService.CheckoutItem(1L, 3)), null);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(paymentGateway).charge(any(), any(), any());
        verify(orderReservationService).reserve(any(Order.class));
        verify(orderReservationService).updateStatus(any(Order.class), eq(OrderStatus.PAID));
    }

    @Test
    void checkoutWithMultipleProductsComputesTotalAndItemsCorrectly() {
        Product product1 = Product.builder().id(1L).price(BigDecimal.valueOf(10)).stockQuantity(5).build();
        Product product2 = Product.builder().id(2L).price(BigDecimal.valueOf(5)).stockQuantity(10).build();
        when(productService.findById(1L)).thenReturn(Optional.of(product1));
        when(productService.findById(2L)).thenReturn(Optional.of(product2));
        when(paymentGateway.charge(any(), any(), any()))
                .thenReturn(new PaymentGateway.PaymentResult(true, "tx-1", "ok"));
        stubReserveAndUpdateStatus();

        List<OrderService.CheckoutItem> items = List.of(
                new OrderService.CheckoutItem(1L, 2),
                new OrderService.CheckoutItem(2L, 3));

        Order result = orderService.checkout(10L, items, null);

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

        assertThatThrownBy(() -> orderService.checkout(10L, items, null))
                .isInstanceOf(InsufficientStockException.class);

        verifyNoInteractions(paymentGateway);
        verifyNoInteractions(orderReservationService);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void checkoutThrowsWhenProductNotFound() {
        when(productService.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.checkout(10L, List.of(new OrderService.CheckoutItem(1L, 1)), null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(paymentGateway);
        verifyNoInteractions(orderReservationService);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void checkoutSavesAsFailedAndDoesNotPublishEventWhenPaymentIsDeclined() {
        Product product = Product.builder().id(1L).price(BigDecimal.TEN).stockQuantity(5).build();
        when(productService.findById(1L)).thenReturn(Optional.of(product));
        when(paymentGateway.charge(any(), any(), any()))
                .thenReturn(new PaymentGateway.PaymentResult(false, null, "declined"));
        stubReserveAndUpdateStatus();

        Order result = orderService.checkout(10L, List.of(new OrderService.CheckoutItem(1L, 2)), null);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.FAILED);
        verify(orderReservationService).reserve(any(Order.class));
        verify(orderReservationService).updateStatus(any(Order.class), eq(OrderStatus.FAILED));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void checkoutMarksOrderFailedInItsOwnTransactionWhenPaymentGatewayThrows() {
        Product product = Product.builder().id(1L).price(BigDecimal.TEN).stockQuantity(5).build();
        when(productService.findById(1L)).thenReturn(Optional.of(product));
        when(orderReservationService.reserve(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentGateway.charge(any(), any(), any())).thenThrow(new IllegalStateException("gateway timeout"));

        assertThatThrownBy(() -> orderService.checkout(10L, List.of(new OrderService.CheckoutItem(1L, 2)), null))
                .isInstanceOf(IllegalStateException.class);

        // updateStatus runs in its own REQUIRES_NEW transaction (OrderReservationService)
        // so the FAILED status survives even though checkout()'s own transaction rolls
        // back when it rethrows the gateway exception.
        verify(orderReservationService).updateStatus(any(Order.class), eq(OrderStatus.FAILED));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void checkoutReturnsExistingOrderForSameBuyerAndIdempotencyKeyWithoutCharging() {
        Order existingOrder = Order.builder().id(99L).buyerId(10L).status(OrderStatus.PAID).build();
        when(orderRepository.findByBuyerIdAndIdempotencyKey(10L, "key-1")).thenReturn(Optional.of(existingOrder));

        Order result = orderService.checkout(10L, List.of(new OrderService.CheckoutItem(1L, 2)), "key-1");

        assertThat(result).isSameAs(existingOrder);
        verifyNoInteractions(paymentGateway, productService, orderReservationService);
    }

    @Test
    void checkoutDoesNotReturnAnotherBuyersOrderForAMatchingIdempotencyKey() {
        Product product = Product.builder().id(1L).price(BigDecimal.TEN).stockQuantity(5).build();
        when(productService.findById(1L)).thenReturn(Optional.of(product));
        when(orderRepository.findByBuyerIdAndIdempotencyKey(20L, "key-1")).thenReturn(Optional.empty());
        when(paymentGateway.charge(any(), any(), any()))
                .thenReturn(new PaymentGateway.PaymentResult(true, "tx-1", "ok"));
        stubReserveAndUpdateStatus();

        Order result = orderService.checkout(20L, List.of(new OrderService.CheckoutItem(1L, 2)), "key-1");

        assertThat(result.getBuyerId()).isEqualTo(20L);
        verify(orderRepository, never()).findByBuyerIdAndIdempotencyKey(10L, "key-1");
        verify(paymentGateway).charge(any(), any(), any());
    }

    @Test
    void checkoutTreatsBlankIdempotencyKeyAsAbsentAndDoesNotPersistBlankString() {
        Product product = Product.builder().id(1L).price(BigDecimal.TEN).stockQuantity(5).build();
        when(productService.findById(1L)).thenReturn(Optional.of(product));
        when(paymentGateway.charge(any(), any(), any()))
                .thenReturn(new PaymentGateway.PaymentResult(true, "tx-1", "ok"));
        stubReserveAndUpdateStatus();

        Order result = orderService.checkout(10L, List.of(new OrderService.CheckoutItem(1L, 2)), "   ");

        assertThat(result.getIdempotencyKey()).isNull();
        verify(orderRepository, never()).findByBuyerIdAndIdempotencyKey(anyLong(), anyString());
    }

    @Test
    void checkoutReturnsExistingOrderWhenConcurrentRequestWinsTheIdempotencyKeyRace() {
        Product product = Product.builder().id(1L).price(BigDecimal.TEN).stockQuantity(5).build();
        Order winningOrder = Order.builder().id(42L).buyerId(10L).status(OrderStatus.PAID).build();
        when(productService.findById(1L)).thenReturn(Optional.of(product));
        when(orderRepository.findByBuyerIdAndIdempotencyKey(10L, "key-1"))
                .thenReturn(Optional.empty(), Optional.of(winningOrder));
        when(orderReservationService.reserve(any(Order.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate idempotency key"));

        Order result = orderService.checkout(10L, List.of(new OrderService.CheckoutItem(1L, 2)), "key-1");

        assertThat(result).isSameAs(winningOrder);
        verifyNoInteractions(paymentGateway, eventPublisher);
        verify(orderReservationService, never()).updateStatus(any(), any());
    }

    @Test
    void findBySellerIdReturnsEmptyListWithoutFetchingOrdersWhenNoOrderIdsMatch() {
        when(orderRepository.findOrderIdsByItemsSellerId(10L)).thenReturn(List.of());

        List<Order> result = orderService.findBySellerId(10L);

        assertThat(result).isEmpty();
        verify(orderRepository, never()).findByIdInWithItems(any());
    }

    @Test
    void findBySellerIdChainsOrderIdLookupThenFetchesOrdersWithItems() {
        Order order = Order.builder().id(5L).build();
        when(orderRepository.findOrderIdsByItemsSellerId(10L)).thenReturn(List.of(5L));
        when(orderRepository.findByIdInWithItems(List.of(5L))).thenReturn(List.of(order));

        List<Order> result = orderService.findBySellerId(10L);

        assertThat(result).containsExactly(order);
    }
}
