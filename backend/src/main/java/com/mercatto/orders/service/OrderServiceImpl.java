package com.mercatto.orders.service;

import com.mercatto.catalog.service.ProductNotFoundException;
import com.mercatto.catalog.service.ProductService;
import com.mercatto.orders.domain.Order;
import com.mercatto.orders.domain.OrderItem;
import com.mercatto.orders.domain.PaymentMethod;
import com.mercatto.orders.domain.ShippingAddress;
import com.mercatto.orders.domain.ShippingMethod;
import com.mercatto.orders.event.OrderPlacedEvent;
import com.mercatto.orders.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Orders calls Catalog synchronously through {@link ProductService} (its
 * public API) to read price/stock at checkout time — a same-transaction,
 * read-then-decide query. The resulting side effect on Catalog's own data
 * (decrementing stock) is not done here; it happens asynchronously via
 * {@link OrderPlacedEvent}, so this transaction never spans two modules'
 * tables.
 */
@Service
@RequiredArgsConstructor
class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderReservationService orderReservationService;
    private final ProductService productService;
    private final PaymentGateway paymentGateway;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Validates stock against the quantity read here before charging, so an
     * out-of-stock item is rejected instead of billed. This is still a
     * read-then-decide check against a snapshot: the actual decrement stays
     * asynchronous (see {@link com.mercatto.orders.event.OrderPlacedEventListener})
     * to keep this transaction from touching Catalog's tables, so two
     * concurrent checkouts of the last unit can both pass this check — that
     * residual race is accepted for ORD-1 and exercised against real
     * concurrency by the Testcontainers suite in QA-4 (#64).
     */
    @Override
    @Transactional
    public Order checkout(
            Long buyerId,
            List<CheckoutItem> items,
            String idempotencyKey,
            ShippingAddress address,
            ShippingMethod shippingMethod,
            PaymentMethod paymentMethod) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);

        if (normalizedKey != null) {
            Optional<Order> existingOrder = orderRepository.findByBuyerIdAndIdempotencyKey(buyerId, normalizedKey);
            if (existingOrder.isPresent()) {
                // A PAID order is a true idempotent replay: return it without charging again.
                // A PENDING/FAILED order means a previous attempt never completed (crash between
                // reserve and charge, or a declined/erroring gateway call) — retry the charge on
                // that same reserved order instead of leaving the buyer permanently stuck on this
                // idempotency key.
                Order existing = existingOrder.get();
                return existing.getStatus() == OrderStatus.PAID ? existing : chargeAndFinalize(existing);
            }
        }

        Order order = Order.builder()
                .buyerId(buyerId)
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .idempotencyKey(normalizedKey)
                .address(address)
                .shippingMethod(shippingMethod)
                .paymentMethod(paymentMethod)
                .build();

        Map<Long, Integer> requestedQuantities = items.stream()
                .collect(Collectors.groupingBy(CheckoutItem::productId, Collectors.summingInt(CheckoutItem::quantity)));

        BigDecimal total = BigDecimal.ZERO;
        for (CheckoutItem checkoutItem : items) {
            ProductService.ProductSummary product = productService.findById(checkoutItem.productId())
                    .orElseThrow(() -> new ProductNotFoundException("Product not found: " + checkoutItem.productId()));

            int requestedQuantity = requestedQuantities.get(checkoutItem.productId());
            if (requestedQuantity > product.stockQuantity()) {
                throw new InsufficientStockException(
                        "Insufficient stock for product " + product.id() + ": requested "
                                + requestedQuantity + ", available " + product.stockQuantity());
            }

            total = total.add(product.price().multiply(BigDecimal.valueOf(checkoutItem.quantity())));

            order.addItem(OrderItem.builder()
                    .productId(product.id())
                    .sellerId(product.sellerId())
                    .quantity(checkoutItem.quantity())
                    .unitPrice(product.price())
                    .build());
        }
        order.setTotalAmount(total);

        // Reserve the idempotency key with an INSERT before charging, in its own
        // transaction (see OrderReservationService): on a duplicate concurrent
        // request, the unique constraint on (buyer_id, idempotency_key) rejects the
        // loser here instead of after a second payment-gateway charge, and rolls
        // back cleanly without aborting this method's own transaction. This also
        // gives the charge below a real, persisted order id instead of null.
        Order reserved;
        try {
            reserved = orderReservationService.reserve(order);
        } catch (DataIntegrityViolationException raceLost) {
            if (normalizedKey == null) {
                throw raceLost;
            }
            Order existing = orderRepository.findByBuyerIdAndIdempotencyKey(buyerId, normalizedKey)
                    .orElseThrow(() -> raceLost);
            return existing.getStatus() == OrderStatus.PAID ? existing : chargeAndFinalize(existing);
        }

        return chargeAndFinalize(reserved);
    }

    private Order chargeAndFinalize(Order order) {
        // Atomically claim the order (PENDING/FAILED -> PROCESSING) before calling the
        // payment gateway. This is a compare-and-swap: if another concurrent request
        // (e.g. a double-submitted retry with the same idempotency key) already claimed
        // it, we must not charge a second time — return the order's current state instead.
        if (!orderReservationService.claimForCharging(order.getId())) {
            return orderRepository.findByIdWithItems(order.getId())
                    .orElseThrow(() -> new IllegalStateException("Order " + order.getId() + " not found during checkout"));
        }

        PaymentGateway.PaymentResult payment;
        try {
            payment = paymentGateway.charge(order.getId(), order.getTotalAmount(), "BRL");
        } catch (RuntimeException chargeFailure) {
            // The reservation already committed in its own transaction, so a hard
            // failure from the gateway (as opposed to a declined PaymentResult) must
            // still mark the order FAILED — otherwise it is stuck PENDING forever
            // and every retry with this idempotency key would keep returning that
            // dead order instead of surfacing the failure.
            orderReservationService.updateStatus(order, OrderStatus.FAILED);
            throw chargeFailure;
        }

        // The PAID/FAILED update also commits in its own transaction: otherwise a
        // failure here (after a successful charge) would roll back with checkout()'s
        // own transaction and leave a charged order stuck PENDING forever, same as
        // the gateway-exception case above.
        Order saved = orderReservationService.updateStatus(
                order, payment.approved() ? OrderStatus.PAID : OrderStatus.FAILED);

        if (payment.approved()) {
            eventPublisher.publishEvent(OrderPlacedEvent.from(saved));
        }

        return saved;
    }

    private static String normalizeIdempotencyKey(String idempotencyKey) {
        return StringUtils.hasText(idempotencyKey) ? idempotencyKey : null;
    }

    @Override
    public Optional<Order> findById(Long id) {
        return orderRepository.findByIdWithItems(id);
    }

    @Override
    public List<Order> findByBuyer(Long buyerId) {
        return orderRepository.findByBuyerIdWithItems(buyerId);
    }

    @Override
    public List<OrderView> findBySellerId(Long sellerId) {
        List<Long> orderIds = orderRepository.findOrderIdsByItemsSellerId(sellerId);
        if (orderIds.isEmpty()) {
            return List.of();
        }
        return orderRepository.findByIdInWithItems(orderIds).stream()
                .map(this::toOrderView)
                .toList();
    }

    @Override
    @Transactional
    public OrderView advanceFulfillment(Long orderId, Long sellerId, FulfillmentStatus next) {
        if (next == null) {
            throw new IllegalArgumentException("Target fulfillment status is required");
        }
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        // Ownership first: a seller with no stake in the order must not learn anything
        // about its state from a 409.
        boolean sellsInOrder = order.getItems().stream()
                .anyMatch(item -> sellerId != null && sellerId.equals(item.getSellerId()));
        if (!sellsInOrder) {
            throw new OrderAccessDeniedException("Seller " + sellerId + " has no items in order " + orderId);
        }
        if (order.getStatus() != OrderStatus.PAID) {
            throw new InvalidFulfillmentTransitionException(
                    "Only PAID orders can be shipped; order " + orderId + " is " + order.getStatus());
        }
        FulfillmentStatus current = order.getFulfillmentStatus();
        if (!current.canAdvanceTo(next)) {
            throw new InvalidFulfillmentTransitionException(
                    "Cannot advance order " + orderId + " from " + current + " to " + next);
        }

        order.advanceFulfillmentTo(next, Instant.now());
        return toOrderView(orderRepository.save(order));
    }

    private OrderView toOrderView(Order order) {
        List<OrderItemView> items = order.getItems().stream()
                .map(item -> new OrderItemView(item.getProductId(), item.getSellerId(), item.getQuantity(), item.getUnitPrice()))
                .toList();
        return new OrderView(order.getId(), order.getBuyerId(), order.getStatus(), order.getFulfillmentStatus(),
                order.getCreatedAt(), items);
    }
}
