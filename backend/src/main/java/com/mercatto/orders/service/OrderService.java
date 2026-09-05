package com.mercatto.orders.service;

import com.mercatto.orders.domain.Order;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.Optional;

/**
 * Public API of the Orders module.
 */
public interface OrderService {

    record CheckoutItem(@NotNull @Positive Long productId, @Positive int quantity) {}

    Order checkout(Long buyerId, List<CheckoutItem> items);

    Optional<Order> findById(Long id);

    List<Order> findByBuyer(Long buyerId);
}
