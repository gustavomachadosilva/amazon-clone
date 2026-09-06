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

    /**
     * Returns the complete orders (all their items, even items belonging to
     * other sellers in the same cart/checkout) that contain at least one of
     * the given product ids. Not filtered down to only the matching items —
     * a deliberate scope decision to keep this composition simple. Intended
     * for cross-module composition (e.g. by Sellers, to look up orders
     * received for a seller's products).
     */
    List<Order> findByProductIds(List<Long> productIds);
}
