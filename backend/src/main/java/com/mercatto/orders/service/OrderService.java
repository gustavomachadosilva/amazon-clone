package com.mercatto.orders.service;

import com.mercatto.orders.domain.Order;
import com.mercatto.orders.domain.PaymentMethod;
import com.mercatto.orders.domain.ShippingAddress;
import com.mercatto.orders.domain.ShippingMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Public API of the Orders module.
 */
public interface OrderService {

    record CheckoutItem(@NotNull @Positive Long productId, @Positive int quantity) {}

    /**
     * Places an order for the given buyer, charging the payment gateway and
     * persisting the address/shipping/payment choices made at checkout as an
     * immutable snapshot on the resulting {@link Order}.
     * <p>
     * When {@code idempotencyKey} matches an order already placed by this
     * buyer (idempotent replay, including a retry of a previously
     * PENDING/FAILED attempt), {@code address}, {@code shippingMethod} and
     * {@code paymentMethod} from this call are ignored — the returned order
     * keeps the values recorded on the original attempt.
     */
    Order checkout(
            Long buyerId,
            List<CheckoutItem> items,
            String idempotencyKey,
            ShippingAddress address,
            ShippingMethod shippingMethod,
            PaymentMethod paymentMethod);

    Optional<Order> findById(Long id);

    List<Order> findByBuyer(Long buyerId);

    record OrderItemView(Long productId, Long sellerId, int quantity, BigDecimal unitPrice) {}

    /**
     * A persistence-free read model of an {@link Order}, used for cross-module reads (e.g. by
     * Sellers) so those modules never depend on orders' JPA entity shape (Contrato de
     * Modularidade regra 3 / Card #142).
     */
    record OrderView(Long id, Long buyerId, OrderStatus status, Instant createdAt, List<OrderItemView> items) {}

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
    List<OrderView> findBySellerId(Long sellerId);
}
