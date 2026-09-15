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

    Order checkout(Long buyerId, List<CheckoutItem> items, String idempotencyKey);

    Optional<Order> findById(Long id);

    List<Order> findByBuyer(Long buyerId);

    /**
     * Returns the complete orders (all their items, even items belonging to
     * other sellers in the same cart/checkout) that contain at least one
     * item sold by the given seller. Not filtered down to only that
     * seller's items — a deliberate scope decision to keep this composition
     * simple; the caller (e.g. Sellers) is expected to filter items itself.
     * Matches on the seller id recorded on each order item at checkout time,
     * so results are unaffected by the seller later deleting the product
     * from the catalog. Intended for cross-module composition (e.g. by
     * Sellers, to look up orders received for its products).
     */
    List<Order> findBySellerId(Long sellerId);
}
