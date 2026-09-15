package com.mercatto.orders.service;

import com.mercatto.orders.domain.Order;
import com.mercatto.orders.domain.OrderStatus;
import com.mercatto.orders.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
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
    void updateStatusSetsTheGivenStatusAndSavesTheOrder() {
        Order order = Order.builder().buyerId(10L).status(OrderStatus.PENDING).build();
        when(orderRepository.save(order)).thenReturn(order);

        Order result = orderReservationService.updateStatus(order, OrderStatus.FAILED);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.FAILED);
        ArgumentCaptor<Order> savedCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getStatus()).isEqualTo(OrderStatus.FAILED);
    }
}
