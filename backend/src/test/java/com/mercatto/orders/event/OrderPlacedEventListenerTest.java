package com.mercatto.orders.event;

import com.mercatto.catalog.service.ProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderPlacedEventListenerTest {

    @Mock
    private ProductService productService;

    @Test
    void retriesAndSucceedsAfterOptimisticLockConflict() {
        OrderPlacedEventListener listener = new OrderPlacedEventListener(productService);
        doThrow(new OptimisticLockingFailureException("stale version"))
                .doThrow(new OptimisticLockingFailureException("stale version"))
                .doNothing()
                .when(productService).decreaseStock(1L, 2);

        listener.onOrderPlaced(new OrderPlacedEvent(10L, 20L, List.of(new OrderPlacedEvent.Item(1L, 2))));

        verify(productService, times(3)).decreaseStock(1L, 2);
    }

    @Test
    void givesUpAfterMaxAttemptsButStillProcessesOtherItems() {
        OrderPlacedEventListener listener = new OrderPlacedEventListener(productService);
        doThrow(new OptimisticLockingFailureException("stale version"))
                .when(productService).decreaseStock(1L, 2);

        listener.onOrderPlaced(new OrderPlacedEvent(10L, 20L, List.of(
                new OrderPlacedEvent.Item(1L, 2),
                new OrderPlacedEvent.Item(2L, 1))));

        verify(productService, times(3)).decreaseStock(1L, 2);
        verify(productService, times(1)).decreaseStock(2L, 1);
    }

    @Test
    void isolatesOneItemFailureFromOthersOnNonRetryableError() {
        OrderPlacedEventListener listener = new OrderPlacedEventListener(productService);
        doThrow(new IllegalStateException("insufficient stock"))
                .when(productService).decreaseStock(1L, 2);

        listener.onOrderPlaced(new OrderPlacedEvent(10L, 20L, List.of(
                new OrderPlacedEvent.Item(1L, 2),
                new OrderPlacedEvent.Item(2L, 1))));

        verify(productService, times(1)).decreaseStock(1L, 2);
        verify(productService, times(1)).decreaseStock(2L, 1);
    }
}
