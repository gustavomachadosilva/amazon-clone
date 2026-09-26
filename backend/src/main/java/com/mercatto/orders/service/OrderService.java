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
    record OrderView(
            Long id,
            Long buyerId,
            OrderStatus status,
            FulfillmentStatus fulfillmentStatus,
            Instant createdAt,
            List<OrderItemView> items) {}

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

    /**
     * Advances the order's shipping lifecycle to {@code next}, stamping the matching
     * timestamp ({@code shippedAt}, {@code outForDeliveryAt} or {@code deliveredAt}).
     * <p>
     * Seller-driven and order-level: any seller with at least one item in the order may
     * advance it, and the new status applies to the whole order (not per item). The order
     * row is locked for the duration of the call so concurrent advances serialize.
     *
     * @throws IllegalArgumentException if {@code next} is null (HTTP 400)
     * @throws OrderNotFoundException if the order does not exist (HTTP 404)
     * @throws OrderAccessDeniedException if {@code sellerId} owns no item in the order —
     *         checked before any state rule, so it never leaks the order's state (HTTP 403)
     * @throws InvalidFulfillmentTransitionException if the order is not PAID, or
     *         {@code next} is not the immediate next {@link FulfillmentStatus} (HTTP 409)
     */
    OrderView advanceFulfillment(Long orderId, Long sellerId, FulfillmentStatus next);

    /**
     * Replaces the shipping address of one of the buyer's orders, as long as it has not
     * shipped yet. Allowed while {@code fulfillmentStatus} is NOT_SHIPPED and the order status
     * is PENDING, PROCESSING, FAILED or PAID; a CANCELLED order is never editable. The order
     * row is locked for the duration of the call so it serializes with a concurrent
     * {@link #advanceFulfillment}. The returned order has its items initialized, so it can be
     * mapped outside the transaction.
     *
     * @throws IllegalArgumentException if {@code address} is null (HTTP 400)
     * @throws OrderNotFoundException if the order does not exist (HTTP 404)
     * @throws OrderAccessDeniedException if {@code buyerId} does not own the order — checked
     *         before any state rule, so it never leaks the order's state (HTTP 403)
     * @throws OrderAddressNotEditableException if the order is CANCELLED or already shipped
     *         (HTTP 409)
     */
    Order updateShippingAddress(Long orderId, Long buyerId, ShippingAddress address);

    /**
     * Retries the payment of one of the buyer's FAILED orders (e.g. declined by the gateway),
     * charging it again with {@code paymentMethod}, which replaces the one recorded on the order.
     * The original {@code totalAmount} is charged — items are not repriced. Before charging, the
     * stock of every item is re-validated against Catalog. The order is claimed atomically
     * (FAILED to PROCESSING) before the gateway call, so two concurrent retries (a double click)
     * never charge twice: the loser gets a 409.
     * <p>
     * If the gateway approves, the order becomes PAID and stock is decremented exactly as after
     * a checkout ({@link com.mercatto.orders.event.OrderPlacedEvent}). If it declines again, the
     * order goes back to FAILED and is returned normally (HTTP 200, same as checkout), so it can
     * be retried again. A gateway error also leaves it FAILED and is rethrown.
     *
     * @throws IllegalArgumentException if {@code paymentMethod} is null (HTTP 400)
     * @throws OrderNotFoundException if the order does not exist (HTTP 404)
     * @throws OrderAccessDeniedException if {@code buyerId} does not own the order — checked
     *         before any state rule, so it never leaks the order's state (HTTP 403)
     * @throws OrderPaymentNotRetryableException if the order is not FAILED, or a concurrent
     *         retry already claimed it (HTTP 409)
     * @throws InsufficientStockException if an item's product no longer has enough stock or no
     *         longer exists (HTTP 409); nothing is charged
     */
    Order retryPayment(Long orderId, Long buyerId, PaymentMethod paymentMethod);
}
