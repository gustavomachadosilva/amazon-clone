package com.mercatto.orders.service;

import com.mercatto.orders.domain.Order;
import com.mercatto.orders.domain.OrderStatus;
import com.mercatto.orders.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists order-state changes that must survive independently of the
 * caller's own transaction outcome. On Postgres, a unique-constraint
 * violation aborts the whole transaction it happened in, so a losing
 * checkout's lookup of the winning order must run outside that aborted
 * transaction; likewise, the PAID/FAILED status update after a payment
 * attempt must commit even if the caller's {@code @Transactional} method
 * later rolls back (e.g. rethrowing a payment-gateway exception) — a payment
 * that already went through must never leave the order stuck PENDING.
 * REQUIRES_NEW gives each operation its own transaction, so it commits or
 * rolls back on its own without touching the caller's.
 */
@Service
@RequiredArgsConstructor
class OrderReservationService {

    private final OrderRepository orderRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order reserve(Order order) {
        return orderRepository.save(order);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order updateStatus(Order order, OrderStatus status) {
        order.setStatus(status);
        return orderRepository.save(order);
    }
}
