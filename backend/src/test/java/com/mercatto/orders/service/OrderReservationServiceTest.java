package com.mercatto.orders.service;

import com.mercatto.orders.domain.Order;
import com.mercatto.orders.domain.PaymentMethod;
import com.mercatto.orders.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderReservationServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderReservationService orderReservationService;

    @Test
    void reserveSavesAndReturnsTheGivenOrder() {
        Order order = Order.builder().buyerId(10L).status(OrderStatus.PENDING).build();
        when(orderRepository.save(order)).thenReturn(order);

        Order result = orderReservationService.reserve(order);

        assertThat(result).isSameAs(order);
    }

    @Test
    void updateStatusWritesOnlyTheStatusAndReturnsTheReloadedOrder() {
        Order stale = Order.builder().id(7L).buyerId(10L).status(OrderStatus.PROCESSING).build();
        Order reloaded = Order.builder().id(7L).buyerId(10L).status(OrderStatus.PAID).build();
        when(orderRepository.findByIdWithItems(7L)).thenReturn(Optional.of(reloaded));

        Order result = orderReservationService.updateStatus(stale, OrderStatus.PAID);

        assertThat(result).isSameAs(reloaded);
        verify(orderRepository).updateStatus(7L, OrderStatus.PAID);
        // Never merges the stale instance: that would revert fields committed during the charge.
        verify(orderRepository, never()).save(any());
    }

    @Test
    void claimFailedForRetrySucceedsOnlyWhenExactlyOneRowWasClaimed() {
        when(orderRepository.claimFailedForRetry(7L, PaymentMethod.GIFT)).thenReturn(1);
        when(orderRepository.claimFailedForRetry(8L, PaymentMethod.GIFT)).thenReturn(0);

        assertThat(orderReservationService.claimFailedForRetry(7L, PaymentMethod.GIFT)).isTrue();
        assertThat(orderReservationService.claimFailedForRetry(8L, PaymentMethod.GIFT)).isFalse();
    }
}
