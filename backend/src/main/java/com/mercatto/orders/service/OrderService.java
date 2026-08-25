package com.mercatto.orders.service;

import com.mercatto.orders.domain.Order;

import java.util.List;
import java.util.Optional;

/**
 * Public API of the Orders module.
 */
public interface OrderService {

    record CheckoutItem(Long productId, int quantity) {}

    Order checkout(Long buyerId, List<CheckoutItem> items);

    Optional<Order> findById(Long id);

    List<Order> findByBuyer(Long buyerId);
}
