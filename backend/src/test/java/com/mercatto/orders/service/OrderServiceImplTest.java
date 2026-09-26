package com.mercatto.orders.service;

import com.mercatto.catalog.service.ProductNotFoundException;
import com.mercatto.catalog.service.ProductService;
import com.mercatto.orders.domain.Order;
import com.mercatto.orders.domain.OrderItem;
import com.mercatto.orders.domain.PaymentMethod;
import com.mercatto.orders.domain.ShippingAddress;
import com.mercatto.orders.domain.ShippingMethod;
import com.mercatto.orders.event.OrderPlacedEvent;
import com.mercatto.orders.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
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
        when(orderReservationService.claimForCharging(nullable(Long.class))).thenReturn(true);
        when(orderReservationService.updateStatus(any(Order.class), any(OrderStatus.class)))
                .thenAnswer(invocation -> {
                    Order order = invocation.getArgument(0);
                    order.setStatus(invocation.getArgument(1));
                    return order;
                });
    }

    private static ProductService.ProductSummary product(long id, BigDecimal price, int stock) {
        return new ProductService.ProductSummary(
                id, "Product " + id, null, price, stock, null, null, null, null, null, null, 20L, null);
    }

    private static ShippingAddress testAddress() {
        return ShippingAddress.builder()
                .fullName("Ada Lovelace")
                .street("1578 Union Street, Apt 92")
                .city("Seattle")
                .state("WA")
                .zip("98104")
                .build();
    }

    @Test
    void checkoutRejectsInsufficientStockWithoutCharging() {
        ProductService.ProductSummary product = product(1L, BigDecimal.TEN, 1);
        when(productService.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> orderService.checkout(10L, List.of(new OrderService.CheckoutItem(1L, 2)), null, testAddress(), ShippingMethod.STANDARD, PaymentMethod.CARD))
                .isInstanceOf(InsufficientStockException.class);

        verifyNoInteractions(paymentGateway);
        verifyNoInteractions(orderReservationService);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void checkoutRejectsInsufficientStockWhenSameProductSpansMultipleLines() {
        ProductService.ProductSummary product = product(1L, BigDecimal.TEN, 5);
        when(productService.findById(1L)).thenReturn(Optional.of(product));

        List<OrderService.CheckoutItem> items = List.of(
                new OrderService.CheckoutItem(1L, 3),
                new OrderService.CheckoutItem(1L, 3));

        assertThatThrownBy(() -> orderService.checkout(10L, items, null, testAddress(), ShippingMethod.STANDARD, PaymentMethod.CARD))
                .isInstanceOf(InsufficientStockException.class);

        verifyNoInteractions(paymentGateway);
        verifyNoInteractions(orderReservationService);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void checkoutChargesAndSavesWhenStockIsSufficient() {
        ProductService.ProductSummary product = product(1L, BigDecimal.TEN, 5);
        when(productService.findById(1L)).thenReturn(Optional.of(product));
        when(paymentGateway.charge(any(), any(), any()))
                .thenReturn(new PaymentGateway.PaymentResult(true, "tx-1", "ok"));
        stubReserveAndUpdateStatus();

        Order result = orderService.checkout(10L, List.of(new OrderService.CheckoutItem(1L, 2)), null, testAddress(), ShippingMethod.STANDARD, PaymentMethod.CARD);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
        // A new order is born NOT_SHIPPED with no shipping timestamps.
        assertThat(result.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.NOT_SHIPPED);
        assertThat(result.getShippedAt()).isNull();
        assertThat(result.getOutForDeliveryAt()).isNull();
        assertThat(result.getDeliveredAt()).isNull();
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
        ProductService.ProductSummary product = product(1L, BigDecimal.TEN, 3);
        when(productService.findById(1L)).thenReturn(Optional.of(product));
        when(paymentGateway.charge(any(), any(), any()))
                .thenReturn(new PaymentGateway.PaymentResult(true, "tx-1", "ok"));
        stubReserveAndUpdateStatus();

        Order result = orderService.checkout(10L, List.of(new OrderService.CheckoutItem(1L, 3)), null, testAddress(), ShippingMethod.STANDARD, PaymentMethod.CARD);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(paymentGateway).charge(any(), any(), any());
        verify(orderReservationService).reserve(any(Order.class));
        verify(orderReservationService).updateStatus(any(Order.class), eq(OrderStatus.PAID));
    }

    @Test
    void checkoutWithMultipleProductsComputesTotalAndItemsCorrectly() {
        ProductService.ProductSummary product1 = product(1L, BigDecimal.valueOf(10), 5);
        ProductService.ProductSummary product2 = product(2L, BigDecimal.valueOf(5), 10);
        when(productService.findById(1L)).thenReturn(Optional.of(product1));
        when(productService.findById(2L)).thenReturn(Optional.of(product2));
        when(paymentGateway.charge(any(), any(), any()))
                .thenReturn(new PaymentGateway.PaymentResult(true, "tx-1", "ok"));
        stubReserveAndUpdateStatus();

        List<OrderService.CheckoutItem> items = List.of(
                new OrderService.CheckoutItem(1L, 2),
                new OrderService.CheckoutItem(2L, 3));

        Order result = orderService.checkout(10L, items, null, testAddress(), ShippingMethod.STANDARD, PaymentMethod.CARD);

        assertThat(result.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(35));
        assertThat(result.getItems())
                .extracting(OrderItem::getProductId, OrderItem::getQuantity, OrderItem::getUnitPrice)
                .containsExactly(
                        tuple(1L, 2, BigDecimal.valueOf(10)),
                        tuple(2L, 3, BigDecimal.valueOf(5)));
    }

    @Test
    void checkoutRejectsWhenOneOfSeveralItemsHasInsufficientStock() {
        ProductService.ProductSummary product1 = product(1L, BigDecimal.TEN, 5);
        ProductService.ProductSummary product2 = product(2L, BigDecimal.valueOf(5), 1);
        when(productService.findById(1L)).thenReturn(Optional.of(product1));
        when(productService.findById(2L)).thenReturn(Optional.of(product2));

        List<OrderService.CheckoutItem> items = List.of(
                new OrderService.CheckoutItem(1L, 2),
                new OrderService.CheckoutItem(2L, 5));

        assertThatThrownBy(() -> orderService.checkout(10L, items, null, testAddress(), ShippingMethod.STANDARD, PaymentMethod.CARD))
                .isInstanceOf(InsufficientStockException.class);

        verifyNoInteractions(paymentGateway);
        verifyNoInteractions(orderReservationService);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void checkoutThrowsWhenProductNotFound() {
        when(productService.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.checkout(10L, List.of(new OrderService.CheckoutItem(1L, 1)), null, testAddress(), ShippingMethod.STANDARD, PaymentMethod.CARD))
                .isInstanceOf(ProductNotFoundException.class);

        verifyNoInteractions(paymentGateway);
        verifyNoInteractions(orderReservationService);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void checkoutSavesAsFailedAndDoesNotPublishEventWhenPaymentIsDeclined() {
        ProductService.ProductSummary product = product(1L, BigDecimal.TEN, 5);
        when(productService.findById(1L)).thenReturn(Optional.of(product));
        when(paymentGateway.charge(any(), any(), any()))
                .thenReturn(new PaymentGateway.PaymentResult(false, null, "declined"));
        stubReserveAndUpdateStatus();

        Order result = orderService.checkout(10L, List.of(new OrderService.CheckoutItem(1L, 2)), null, testAddress(), ShippingMethod.STANDARD, PaymentMethod.CARD);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.FAILED);
        verify(orderReservationService).reserve(any(Order.class));
        verify(orderReservationService).updateStatus(any(Order.class), eq(OrderStatus.FAILED));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void checkoutMarksOrderFailedInItsOwnTransactionWhenPaymentGatewayThrows() {
        ProductService.ProductSummary product = product(1L, BigDecimal.TEN, 5);
        when(productService.findById(1L)).thenReturn(Optional.of(product));
        when(orderReservationService.reserve(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderReservationService.claimForCharging(nullable(Long.class))).thenReturn(true);
        when(paymentGateway.charge(any(), any(), any())).thenThrow(new IllegalStateException("gateway timeout"));

        assertThatThrownBy(() -> orderService.checkout(10L, List.of(new OrderService.CheckoutItem(1L, 2)), null, testAddress(), ShippingMethod.STANDARD, PaymentMethod.CARD))
                .isInstanceOf(IllegalStateException.class);

        // updateStatus runs in its own REQUIRES_NEW transaction (OrderReservationService)
        // so the FAILED status survives even though checkout()'s own transaction rolls
        // back when it rethrows the gateway exception.
        verify(orderReservationService).updateStatus(any(Order.class), eq(OrderStatus.FAILED));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void checkoutPersistsAddressShippingMethodAndPaymentMethodOnNewOrder() {
        ProductService.ProductSummary product = product(1L, BigDecimal.TEN, 5);
        when(productService.findById(1L)).thenReturn(Optional.of(product));
        when(paymentGateway.charge(any(), any(), any()))
                .thenReturn(new PaymentGateway.PaymentResult(true, "tx-1", "ok"));
        stubReserveAndUpdateStatus();
        ShippingAddress address = testAddress();

        Order result = orderService.checkout(
                10L, List.of(new OrderService.CheckoutItem(1L, 2)), null, address, ShippingMethod.EXPRESS, PaymentMethod.GIFT);

        assertThat(result.getAddress()).isEqualTo(address);
        assertThat(result.getShippingMethod()).isEqualTo(ShippingMethod.EXPRESS);
        assertThat(result.getPaymentMethod()).isEqualTo(PaymentMethod.GIFT);
    }

    @Test
    void checkoutReturnsExistingOrderForSameBuyerAndIdempotencyKeyWithoutCharging() {
        Order existingOrder = Order.builder().id(99L).buyerId(10L).status(OrderStatus.PAID).build();
        when(orderRepository.findByBuyerIdAndIdempotencyKey(10L, "key-1")).thenReturn(Optional.of(existingOrder));

        Order result = orderService.checkout(10L, List.of(new OrderService.CheckoutItem(1L, 2)), "key-1", testAddress(), ShippingMethod.STANDARD, PaymentMethod.CARD);

        assertThat(result).isSameAs(existingOrder);
        verifyNoInteractions(paymentGateway, productService, orderReservationService);
    }

    @Test
    void checkoutIdempotentReplayIgnoresAddressAndPaymentFromTheReplayRequest() {
        ShippingAddress originalAddress = ShippingAddress.builder()
                .fullName("Original Buyer")
                .street("Original Street")
                .city("Original City")
                .state("OS")
                .zip("00000")
                .build();
        Order existingOrder = Order.builder()
                .id(99L)
                .buyerId(10L)
                .status(OrderStatus.PAID)
                .address(originalAddress)
                .shippingMethod(ShippingMethod.STANDARD)
                .paymentMethod(PaymentMethod.CARD)
                .build();
        when(orderRepository.findByBuyerIdAndIdempotencyKey(10L, "key-1")).thenReturn(Optional.of(existingOrder));

        ShippingAddress replayAddress = ShippingAddress.builder()
                .fullName("Different Buyer")
                .street("Different Street")
                .city("Different City")
                .state("DS")
                .zip("11111")
                .build();

        Order result = orderService.checkout(
                10L, List.of(new OrderService.CheckoutItem(1L, 2)), "key-1", replayAddress, ShippingMethod.EXPRESS, PaymentMethod.GIFT);

        assertThat(result).isSameAs(existingOrder);
        assertThat(result.getAddress()).isEqualTo(originalAddress);
        assertThat(result.getShippingMethod()).isEqualTo(ShippingMethod.STANDARD);
        assertThat(result.getPaymentMethod()).isEqualTo(PaymentMethod.CARD);
        verifyNoInteractions(paymentGateway, productService, orderReservationService);
    }

    @Test
    void checkoutDoesNotReturnAnotherBuyersOrderForAMatchingIdempotencyKey() {
        ProductService.ProductSummary product = product(1L, BigDecimal.TEN, 5);
        when(productService.findById(1L)).thenReturn(Optional.of(product));
        when(orderRepository.findByBuyerIdAndIdempotencyKey(20L, "key-1")).thenReturn(Optional.empty());
        when(paymentGateway.charge(any(), any(), any()))
                .thenReturn(new PaymentGateway.PaymentResult(true, "tx-1", "ok"));
        stubReserveAndUpdateStatus();

        Order result = orderService.checkout(20L, List.of(new OrderService.CheckoutItem(1L, 2)), "key-1", testAddress(), ShippingMethod.STANDARD, PaymentMethod.CARD);

        assertThat(result.getBuyerId()).isEqualTo(20L);
        verify(orderRepository, never()).findByBuyerIdAndIdempotencyKey(10L, "key-1");
        verify(paymentGateway).charge(any(), any(), any());
    }

    @Test
    void checkoutTreatsBlankIdempotencyKeyAsAbsentAndDoesNotPersistBlankString() {
        ProductService.ProductSummary product = product(1L, BigDecimal.TEN, 5);
        when(productService.findById(1L)).thenReturn(Optional.of(product));
        when(paymentGateway.charge(any(), any(), any()))
                .thenReturn(new PaymentGateway.PaymentResult(true, "tx-1", "ok"));
        stubReserveAndUpdateStatus();

        Order result = orderService.checkout(10L, List.of(new OrderService.CheckoutItem(1L, 2)), "   ", testAddress(), ShippingMethod.STANDARD, PaymentMethod.CARD);

        assertThat(result.getIdempotencyKey()).isNull();
        verify(orderRepository, never()).findByBuyerIdAndIdempotencyKey(anyLong(), anyString());
    }

    @Test
    void checkoutRetriesPaymentWhenExistingIdempotentOrderIsFailed() {
        Order existingOrder = Order.builder().id(99L).buyerId(10L).status(OrderStatus.FAILED).totalAmount(BigDecimal.TEN).build();
        when(orderRepository.findByBuyerIdAndIdempotencyKey(10L, "key-1")).thenReturn(Optional.of(existingOrder));
        when(orderReservationService.claimForCharging(99L)).thenReturn(true);
        when(paymentGateway.charge(eq(99L), eq(BigDecimal.TEN), any()))
                .thenReturn(new PaymentGateway.PaymentResult(true, "tx-2", "ok"));
        when(orderReservationService.updateStatus(any(Order.class), any(OrderStatus.class)))
                .thenAnswer(invocation -> {
                    Order order = invocation.getArgument(0);
                    order.setStatus(invocation.getArgument(1));
                    return order;
                });

        Order result = orderService.checkout(10L, List.of(new OrderService.CheckoutItem(1L, 2)), "key-1", testAddress(), ShippingMethod.STANDARD, PaymentMethod.CARD);

        assertThat(result).isSameAs(existingOrder);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(paymentGateway).charge(eq(99L), eq(BigDecimal.TEN), any());
        verify(orderReservationService, never()).reserve(any());
        verify(orderReservationService).updateStatus(existingOrder, OrderStatus.PAID);
        verify(eventPublisher).publishEvent(any(OrderPlacedEvent.class));
        verifyNoInteractions(productService);
    }

    @Test
    void checkoutRetriesPaymentWhenExistingIdempotentOrderIsPending() {
        Order existingOrder = Order.builder().id(99L).buyerId(10L).status(OrderStatus.PENDING).totalAmount(BigDecimal.TEN).build();
        when(orderRepository.findByBuyerIdAndIdempotencyKey(10L, "key-1")).thenReturn(Optional.of(existingOrder));
        when(orderReservationService.claimForCharging(99L)).thenReturn(true);
        when(paymentGateway.charge(eq(99L), eq(BigDecimal.TEN), any()))
                .thenReturn(new PaymentGateway.PaymentResult(false, null, "declined"));
        when(orderReservationService.updateStatus(any(Order.class), any(OrderStatus.class)))
                .thenAnswer(invocation -> {
                    Order order = invocation.getArgument(0);
                    order.setStatus(invocation.getArgument(1));
                    return order;
                });

        Order result = orderService.checkout(10L, List.of(new OrderService.CheckoutItem(1L, 2)), "key-1", testAddress(), ShippingMethod.STANDARD, PaymentMethod.CARD);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.FAILED);
        verify(paymentGateway).charge(eq(99L), eq(BigDecimal.TEN), any());
        verify(orderReservationService, never()).reserve(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void checkoutDoesNotChargeAgainWhenConcurrentRetryAlreadyClaimedTheOrder() {
        Order existingOrder = Order.builder().id(99L).buyerId(10L).status(OrderStatus.PENDING).totalAmount(BigDecimal.TEN).build();
        Order stillProcessing = Order.builder().id(99L).buyerId(10L).status(OrderStatus.PROCESSING).totalAmount(BigDecimal.TEN).build();
        when(orderRepository.findByBuyerIdAndIdempotencyKey(10L, "key-1")).thenReturn(Optional.of(existingOrder));
        when(orderReservationService.claimForCharging(99L)).thenReturn(false);
        when(orderRepository.findByIdWithItems(99L)).thenReturn(Optional.of(stillProcessing));

        Order result = orderService.checkout(10L, List.of(new OrderService.CheckoutItem(1L, 2)), "key-1", testAddress(), ShippingMethod.STANDARD, PaymentMethod.CARD);

        assertThat(result).isSameAs(stillProcessing);
        verifyNoInteractions(paymentGateway, eventPublisher);
        verify(orderReservationService, never()).updateStatus(any(), any());
    }

    @Test
    void checkoutReturnsExistingOrderWhenConcurrentRequestWinsTheIdempotencyKeyRace() {
        ProductService.ProductSummary product = product(1L, BigDecimal.TEN, 5);
        Order winningOrder = Order.builder().id(42L).buyerId(10L).status(OrderStatus.PAID).build();
        when(productService.findById(1L)).thenReturn(Optional.of(product));
        when(orderRepository.findByBuyerIdAndIdempotencyKey(10L, "key-1"))
                .thenReturn(Optional.empty(), Optional.of(winningOrder));
        when(orderReservationService.reserve(any(Order.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate idempotency key"));

        Order result = orderService.checkout(10L, List.of(new OrderService.CheckoutItem(1L, 2)), "key-1", testAddress(), ShippingMethod.STANDARD, PaymentMethod.CARD);

        assertThat(result).isSameAs(winningOrder);
        verifyNoInteractions(paymentGateway, eventPublisher);
        verify(orderReservationService, never()).updateStatus(any(), any());
    }

    @Test
    void findBySellerIdReturnsEmptyListWithoutFetchingOrdersWhenNoOrderIdsMatch() {
        when(orderRepository.findOrderIdsByItemsSellerId(10L)).thenReturn(List.of());

        List<OrderService.OrderView> result = orderService.findBySellerId(10L);

        assertThat(result).isEmpty();
        verify(orderRepository, never()).findByIdInWithItems(any());
    }

    @Test
    void findBySellerIdChainsOrderIdLookupThenFetchesOrdersWithItems() {
        Order order = Order.builder().id(5L).buyerId(20L).status(OrderStatus.PAID).build();
        order.addItem(OrderItem.builder().productId(1L).sellerId(10L).quantity(2).unitPrice(BigDecimal.TEN).build());
        when(orderRepository.findOrderIdsByItemsSellerId(10L)).thenReturn(List.of(5L));
        when(orderRepository.findByIdInWithItems(List.of(5L))).thenReturn(List.of(order));

        List<OrderService.OrderView> result = orderService.findBySellerId(10L);

        assertThat(result).containsExactly(new OrderService.OrderView(5L, 20L, OrderStatus.PAID,
                FulfillmentStatus.NOT_SHIPPED, order.getCreatedAt(),
                List.of(new OrderService.OrderItemView(1L, 10L, 2, BigDecimal.TEN))));
    }

    @Test
    void findBySellerIdMapsFulfillmentStatus() {
        Order order = Order.builder().id(5L).buyerId(20L).status(OrderStatus.PAID)
                .fulfillmentStatus(FulfillmentStatus.OUT_FOR_DELIVERY).build();
        order.addItem(OrderItem.builder().productId(1L).sellerId(10L).quantity(1).unitPrice(BigDecimal.TEN).build());
        when(orderRepository.findOrderIdsByItemsSellerId(10L)).thenReturn(List.of(5L));
        when(orderRepository.findByIdInWithItems(List.of(5L))).thenReturn(List.of(order));

        List<OrderService.OrderView> result = orderService.findBySellerId(10L);

        assertThat(result).extracting(OrderService.OrderView::fulfillmentStatus)
                .containsExactly(FulfillmentStatus.OUT_FOR_DELIVERY);
    }

    // --- advanceFulfillment ---------------------------------------------------------------

    private static Order paidOrder(FulfillmentStatus fulfillmentStatus, Long... sellerIds) {
        Order order = Order.builder()
                .id(7L)
                .buyerId(20L)
                .status(OrderStatus.PAID)
                .fulfillmentStatus(fulfillmentStatus)
                .totalAmount(BigDecimal.TEN)
                .build();
        long productId = 1L;
        for (Long sellerId : sellerIds) {
            order.addItem(OrderItem.builder()
                    .productId(productId++).sellerId(sellerId).quantity(1).unitPrice(BigDecimal.TEN).build());
        }
        return order;
    }

    private void stubLockedOrder(Order order) {
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
    }

    private void stubSaveReturnsArgument() {
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void advanceFulfillmentFromNotShippedToShippedSetsOnlyShippedAtAndSaves() {
        Order order = paidOrder(FulfillmentStatus.NOT_SHIPPED, 10L);
        stubLockedOrder(order);
        stubSaveReturnsArgument();
        Instant before = Instant.now();

        OrderService.OrderView view = orderService.advanceFulfillment(7L, 10L, FulfillmentStatus.SHIPPED);

        assertThat(view.fulfillmentStatus()).isEqualTo(FulfillmentStatus.SHIPPED);
        assertThat(view.id()).isEqualTo(7L);
        assertThat(order.getFulfillmentStatus()).isEqualTo(FulfillmentStatus.SHIPPED);
        assertThat(order.getShippedAt()).isNotNull().isAfterOrEqualTo(before);
        assertThat(order.getOutForDeliveryAt()).isNull();
        assertThat(order.getDeliveredAt()).isNull();
        verify(orderRepository).save(order);
    }

    @Test
    void advanceFulfillmentFromShippedToOutForDeliverySetsOutForDeliveryAt() {
        Order order = paidOrder(FulfillmentStatus.SHIPPED, 10L);
        stubLockedOrder(order);
        stubSaveReturnsArgument();

        OrderService.OrderView view = orderService.advanceFulfillment(7L, 10L, FulfillmentStatus.OUT_FOR_DELIVERY);

        assertThat(view.fulfillmentStatus()).isEqualTo(FulfillmentStatus.OUT_FOR_DELIVERY);
        assertThat(order.getOutForDeliveryAt()).isNotNull();
        assertThat(order.getDeliveredAt()).isNull();
        verify(orderRepository).save(order);
    }

    @Test
    void advanceFulfillmentFromOutForDeliveryToDeliveredSetsDeliveredAt() {
        Order order = paidOrder(FulfillmentStatus.OUT_FOR_DELIVERY, 10L);
        stubLockedOrder(order);
        stubSaveReturnsArgument();

        OrderService.OrderView view = orderService.advanceFulfillment(7L, 10L, FulfillmentStatus.DELIVERED);

        assertThat(view.fulfillmentStatus()).isEqualTo(FulfillmentStatus.DELIVERED);
        assertThat(order.getDeliveredAt()).isNotNull();
        verify(orderRepository).save(order);
    }

    @Test
    void advanceFulfillmentIsAllowedForAnySellerWithAnItemInAMultiSellerOrder() {
        Order order = paidOrder(FulfillmentStatus.NOT_SHIPPED, 10L, 30L);
        stubLockedOrder(order);
        stubSaveReturnsArgument();

        OrderService.OrderView view = orderService.advanceFulfillment(7L, 30L, FulfillmentStatus.SHIPPED);

        assertThat(view.fulfillmentStatus()).isEqualTo(FulfillmentStatus.SHIPPED);
        verify(orderRepository).save(order);
    }

    static Stream<Arguments> invalidTransitions() {
        return Stream.of(
                Arguments.of(FulfillmentStatus.NOT_SHIPPED, FulfillmentStatus.OUT_FOR_DELIVERY),
                Arguments.of(FulfillmentStatus.NOT_SHIPPED, FulfillmentStatus.DELIVERED),
                Arguments.of(FulfillmentStatus.SHIPPED, FulfillmentStatus.DELIVERED),
                Arguments.of(FulfillmentStatus.SHIPPED, FulfillmentStatus.NOT_SHIPPED),
                Arguments.of(FulfillmentStatus.DELIVERED, FulfillmentStatus.SHIPPED),
                Arguments.of(FulfillmentStatus.SHIPPED, FulfillmentStatus.SHIPPED),
                Arguments.of(FulfillmentStatus.DELIVERED, FulfillmentStatus.DELIVERED));
    }

    @ParameterizedTest
    @MethodSource("invalidTransitions")
    void advanceFulfillmentRejectsAnythingButTheImmediateNextState(FulfillmentStatus current, FulfillmentStatus next) {
        Order order = paidOrder(current, 10L);
        stubLockedOrder(order);

        assertThatThrownBy(() -> orderService.advanceFulfillment(7L, 10L, next))
                .isInstanceOf(InvalidFulfillmentTransitionException.class)
                .hasMessageContaining(current.name())
                .hasMessageContaining(next.name());

        assertThat(order.getFulfillmentStatus()).isEqualTo(current);
        verify(orderRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "PAID", mode = EnumSource.Mode.EXCLUDE)
    void advanceFulfillmentRejectsOrdersThatAreNotPaid(OrderStatus status) {
        Order order = paidOrder(FulfillmentStatus.NOT_SHIPPED, 10L);
        order.setStatus(status);
        stubLockedOrder(order);

        assertThatThrownBy(() -> orderService.advanceFulfillment(7L, 10L, FulfillmentStatus.SHIPPED))
                .isInstanceOf(InvalidFulfillmentTransitionException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void advanceFulfillmentRejectsSellerWithoutItemsBeforeCheckingOrderState() {
        // Not PAID and an invalid transition too: ownership must be checked first (403, not 409).
        Order order = paidOrder(FulfillmentStatus.NOT_SHIPPED, 10L);
        order.setStatus(OrderStatus.PENDING);
        stubLockedOrder(order);

        assertThatThrownBy(() -> orderService.advanceFulfillment(7L, 99L, FulfillmentStatus.DELIVERED))
                .isInstanceOf(OrderAccessDeniedException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void advanceFulfillmentRejectsSellerWhenOrderItemsHaveNoSellerId() {
        Order order = paidOrder(FulfillmentStatus.NOT_SHIPPED, (Long) null);
        stubLockedOrder(order);

        assertThatThrownBy(() -> orderService.advanceFulfillment(7L, 10L, FulfillmentStatus.SHIPPED))
                .isInstanceOf(OrderAccessDeniedException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void advanceFulfillmentThrowsWhenOrderDoesNotExist() {
        when(orderRepository.findByIdForUpdate(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.advanceFulfillment(7L, 10L, FulfillmentStatus.SHIPPED))
                .isInstanceOf(OrderNotFoundException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void advanceFulfillmentRejectsNullTargetStatus() {
        assertThatThrownBy(() -> orderService.advanceFulfillment(7L, 10L, null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(orderRepository);
    }

    // --- updateShippingAddress ------------------------------------------------------------

    private static final Long BUYER_ID = 20L;

    private static ShippingAddress newAddress() {
        return ShippingAddress.builder()
                .fullName("Grace Hopper")
                .street("200 Navy Way")
                .city("Arlington")
                .state("VA")
                .zip("22202")
                .build();
    }

    private static Order buyerOrder(OrderStatus status, FulfillmentStatus fulfillmentStatus) {
        Order order = paidOrder(fulfillmentStatus, 10L);
        order.setStatus(status);
        order.setAddress(testAddress());
        return order;
    }

    @Test
    void updateShippingAddressReplacesTheAddressAndSaves() {
        Order order = buyerOrder(OrderStatus.PAID, FulfillmentStatus.NOT_SHIPPED);
        stubLockedOrder(order);
        stubSaveReturnsArgument();
        ShippingAddress address = newAddress();

        Order updated = orderService.updateShippingAddress(7L, BUYER_ID, address);

        assertThat(updated).isSameAs(order);
        assertThat(updated.getAddress()).isSameAs(address);
        assertThat(updated.getAddress().getFullName()).isEqualTo("Grace Hopper");
        assertThat(updated.getItems()).hasSize(1);
        verify(orderRepository).save(order);
    }

    @Test
    void updateShippingAddressThrowsWhenOrderDoesNotExist() {
        when(orderRepository.findByIdForUpdate(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.updateShippingAddress(7L, BUYER_ID, newAddress()))
                .isInstanceOf(OrderNotFoundException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void updateShippingAddressRejectsNonOwnerBeforeCheckingOrderState() {
        // Already shipped too: ownership must be checked first (403, not 409).
        Order order = buyerOrder(OrderStatus.PAID, FulfillmentStatus.SHIPPED);
        stubLockedOrder(order);

        assertThatThrownBy(() -> orderService.updateShippingAddress(7L, 99L, newAddress()))
                .isInstanceOf(OrderAccessDeniedException.class);

        assertThat(order.getAddress().getFullName()).isEqualTo("Ada Lovelace");
        verify(orderRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = FulfillmentStatus.class, names = "NOT_SHIPPED", mode = EnumSource.Mode.EXCLUDE)
    void updateShippingAddressRejectsOrdersThatAlreadyShipped(FulfillmentStatus fulfillmentStatus) {
        Order order = buyerOrder(OrderStatus.PAID, fulfillmentStatus);
        stubLockedOrder(order);

        assertThatThrownBy(() -> orderService.updateShippingAddress(7L, BUYER_ID, newAddress()))
                .isInstanceOf(OrderAddressNotEditableException.class)
                .hasMessage("Order already shipped");

        assertThat(order.getAddress().getFullName()).isEqualTo("Ada Lovelace");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void updateShippingAddressRejectsCancelledOrders() {
        Order order = buyerOrder(OrderStatus.CANCELLED, FulfillmentStatus.NOT_SHIPPED);
        stubLockedOrder(order);

        assertThatThrownBy(() -> orderService.updateShippingAddress(7L, BUYER_ID, newAddress()))
                .isInstanceOf(OrderAddressNotEditableException.class)
                .hasMessage("Order is cancelled");

        assertThat(order.getAddress().getFullName()).isEqualTo("Ada Lovelace");
        verify(orderRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PENDING", "PROCESSING", "FAILED", "PAID"})
    void updateShippingAddressIsAllowedForEveryNonCancelledStatusWhileNotShipped(OrderStatus status) {
        Order order = buyerOrder(status, FulfillmentStatus.NOT_SHIPPED);
        stubLockedOrder(order);
        stubSaveReturnsArgument();

        Order updated = orderService.updateShippingAddress(7L, BUYER_ID, newAddress());

        assertThat(updated.getAddress().getFullName()).isEqualTo("Grace Hopper");
        verify(orderRepository).save(order);
    }

    @Test
    void updateShippingAddressRejectsNullAddress() {
        assertThatThrownBy(() -> orderService.updateShippingAddress(7L, BUYER_ID, null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(orderRepository);
    }

    // --- retryPayment ---------------------------------------------------------------------

    private static final BigDecimal RETRY_TOTAL = new BigDecimal("30.00");

    /** A FAILED order of BUYER_ID paid by CARD: product 1 x2 and product 2 x1. */
    private static Order failedOrder(OrderStatus status) {
        Order order = Order.builder()
                .id(7L)
                .buyerId(BUYER_ID)
                .status(status)
                .totalAmount(RETRY_TOTAL)
                .paymentMethod(PaymentMethod.CARD)
                .build();
        order.addItem(OrderItem.builder().productId(1L).sellerId(30L).quantity(2).unitPrice(BigDecimal.TEN).build());
        order.addItem(OrderItem.builder().productId(2L).sellerId(30L).quantity(1).unitPrice(BigDecimal.TEN).build());
        return order;
    }

    private void stubRetryableOrder(Order order) {
        when(orderRepository.findByIdWithItems(7L)).thenReturn(Optional.of(order));
    }

    private void stubStockAvailable() {
        // Prices differ from the order's unit prices on purpose: the retry must not reprice.
        when(productService.findById(1L)).thenReturn(Optional.of(product(1L, new BigDecimal("99.00"), 5)));
        when(productService.findById(2L)).thenReturn(Optional.of(product(2L, new BigDecimal("99.00"), 1)));
    }

    private void stubUpdateStatus() {
        when(orderReservationService.updateStatus(any(Order.class), any(OrderStatus.class)))
                .thenAnswer(invocation -> {
                    Order order = invocation.getArgument(0);
                    order.setStatus(invocation.getArgument(1));
                    return order;
                });
    }

    @Test
    void retryPaymentChargesTheOriginalTotalWithTheNewMethodAndPublishesOrderPlaced() {
        Order order = failedOrder(OrderStatus.FAILED);
        stubRetryableOrder(order);
        stubStockAvailable();
        // The claim commits the new method on the row; updateStatus returns the reloaded row, which
        // the stub below stands in for by returning this same instance.
        when(orderReservationService.claimFailedForRetry(7L, PaymentMethod.GIFT)).thenAnswer(invocation -> {
            order.setPaymentMethod(PaymentMethod.GIFT);
            return true;
        });
        stubUpdateStatus();
        when(paymentGateway.charge(eq(7L), any(), eq("BRL")))
                .thenReturn(new PaymentGateway.PaymentResult(true, "tx_1", "approved"));

        Order result = orderService.retryPayment(7L, BUYER_ID, PaymentMethod.GIFT);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(result.getPaymentMethod()).isEqualTo(PaymentMethod.GIFT);
        verify(orderReservationService).claimFailedForRetry(7L, PaymentMethod.GIFT);
        verify(orderReservationService, never()).claimForCharging(anyLong());
        verify(paymentGateway).charge(7L, RETRY_TOTAL, "BRL");
        verify(orderReservationService).updateStatus(order, OrderStatus.PAID);
        ArgumentCaptor<OrderPlacedEvent> event = ArgumentCaptor.forClass(OrderPlacedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().orderId()).isEqualTo(7L);
        assertThat(event.getValue().items())
                .extracting(OrderPlacedEvent.Item::productId, OrderPlacedEvent.Item::quantity)
                .containsExactlyInAnyOrder(tuple(1L, 2), tuple(2L, 1));
    }

    @Test
    void retryPaymentDeclinedAgainLeavesTheOrderFailedWithoutPublishing() {
        Order order = failedOrder(OrderStatus.FAILED);
        stubRetryableOrder(order);
        stubStockAvailable();
        when(orderReservationService.claimFailedForRetry(7L, PaymentMethod.CARD)).thenReturn(true);
        stubUpdateStatus();
        when(paymentGateway.charge(anyLong(), any(), anyString()))
                .thenReturn(new PaymentGateway.PaymentResult(false, null, "declined"));

        Order result = orderService.retryPayment(7L, BUYER_ID, PaymentMethod.CARD);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.FAILED);
        verify(orderReservationService).updateStatus(any(Order.class), eq(OrderStatus.FAILED));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void retryPaymentMarksTheOrderFailedAndRethrowsWhenTheGatewayThrows() {
        Order order = failedOrder(OrderStatus.FAILED);
        stubRetryableOrder(order);
        stubStockAvailable();
        when(orderReservationService.claimFailedForRetry(7L, PaymentMethod.CARD)).thenReturn(true);
        when(paymentGateway.charge(anyLong(), any(), anyString())).thenThrow(new IllegalStateException("gateway timeout"));

        assertThatThrownBy(() -> orderService.retryPayment(7L, BUYER_ID, PaymentMethod.CARD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("gateway timeout");

        verify(orderReservationService).updateStatus(any(Order.class), eq(OrderStatus.FAILED));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void retryPaymentThrowsWhenOrderDoesNotExist() {
        when(orderRepository.findByIdWithItems(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.retryPayment(7L, BUYER_ID, PaymentMethod.CARD))
                .isInstanceOf(OrderNotFoundException.class);

        verifyNoInteractions(orderReservationService, productService, paymentGateway, eventPublisher);
    }

    @Test
    void retryPaymentRejectsNonOwnerBeforeCheckingOrderState() {
        // PAID too: ownership must be checked first (403, not 409).
        stubRetryableOrder(failedOrder(OrderStatus.PAID));

        assertThatThrownBy(() -> orderService.retryPayment(7L, 99L, PaymentMethod.CARD))
                .isInstanceOf(OrderAccessDeniedException.class);

        verifyNoInteractions(orderReservationService, productService, paymentGateway, eventPublisher);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "FAILED", mode = EnumSource.Mode.EXCLUDE)
    void retryPaymentRejectsOrdersThatAreNotFailed(OrderStatus status) {
        Order order = failedOrder(status);
        stubRetryableOrder(order);

        assertThatThrownBy(() -> orderService.retryPayment(7L, BUYER_ID, PaymentMethod.GIFT))
                .isInstanceOf(OrderPaymentNotRetryableException.class);

        assertThat(order.getPaymentMethod()).isEqualTo(PaymentMethod.CARD);
        verifyNoInteractions(orderReservationService, productService, paymentGateway, eventPublisher);
    }

    @Test
    void retryPaymentRejectsInsufficientStockWithoutClaimingOrCharging() {
        stubRetryableOrder(failedOrder(OrderStatus.FAILED));
        when(productService.findById(1L)).thenReturn(Optional.of(product(1L, BigDecimal.TEN, 1)));
        lenient().when(productService.findById(2L)).thenReturn(Optional.of(product(2L, BigDecimal.TEN, 5)));

        assertThatThrownBy(() -> orderService.retryPayment(7L, BUYER_ID, PaymentMethod.CARD))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("product 1");

        verifyNoInteractions(orderReservationService, paymentGateway, eventPublisher);
    }

    @Test
    void retryPaymentSumsQuantitiesOfTheSameProductAcrossLines() {
        Order order = Order.builder().id(7L).buyerId(BUYER_ID).status(OrderStatus.FAILED)
                .totalAmount(RETRY_TOTAL).paymentMethod(PaymentMethod.CARD).build();
        order.addItem(OrderItem.builder().productId(1L).quantity(2).unitPrice(BigDecimal.TEN).build());
        order.addItem(OrderItem.builder().productId(1L).quantity(2).unitPrice(BigDecimal.TEN).build());
        stubRetryableOrder(order);
        // Each line alone fits in stock 3, but together they need 4.
        when(productService.findById(1L)).thenReturn(Optional.of(product(1L, BigDecimal.TEN, 3)));

        assertThatThrownBy(() -> orderService.retryPayment(7L, BUYER_ID, PaymentMethod.CARD))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("requested 4, available 3");

        verifyNoInteractions(orderReservationService, paymentGateway, eventPublisher);
    }

    @Test
    void retryPaymentRejectsAnItemWhoseProductWasDeleted() {
        stubRetryableOrder(failedOrder(OrderStatus.FAILED));
        when(productService.findById(1L)).thenReturn(Optional.empty());
        lenient().when(productService.findById(2L)).thenReturn(Optional.of(product(2L, BigDecimal.TEN, 5)));

        assertThatThrownBy(() -> orderService.retryPayment(7L, BUYER_ID, PaymentMethod.CARD))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("Product 1 is no longer available");

        verifyNoInteractions(orderReservationService, paymentGateway, eventPublisher);
    }

    @Test
    void retryPaymentDoesNotChargeWhenAConcurrentRetryAlreadyClaimedTheOrder() {
        Order order = failedOrder(OrderStatus.FAILED);
        stubRetryableOrder(order);
        stubStockAvailable();
        when(orderReservationService.claimFailedForRetry(7L, PaymentMethod.CARD)).thenReturn(false);

        assertThatThrownBy(() -> orderService.retryPayment(7L, BUYER_ID, PaymentMethod.CARD))
                .isInstanceOf(OrderPaymentNotRetryableException.class)
                .hasMessageContaining("already being processed");

        verify(orderReservationService, never()).updateStatus(any(), any());
        verifyNoInteractions(paymentGateway, eventPublisher);
    }

    @Test
    void retryPaymentRejectsNullPaymentMethod() {
        assertThatThrownBy(() -> orderService.retryPayment(7L, BUYER_ID, null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(orderRepository, orderReservationService, productService, paymentGateway);
    }
}
