package com.mercatto.orders.service;

import com.mercatto.catalog.domain.Product;
import com.mercatto.catalog.service.ProductService;
import com.mercatto.orders.domain.Order;
import com.mercatto.orders.domain.OrderItem;
import com.mercatto.orders.domain.OrderStatus;
import com.mercatto.orders.event.OrderPlacedEvent;
import com.mercatto.orders.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    public Order checkout(Long buyerId, List<CheckoutItem> items) {
        Order order = Order.builder()
                .buyerId(buyerId)
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .build();

        Map<Long, Integer> requestedQuantities = items.stream()
                .collect(Collectors.groupingBy(CheckoutItem::productId, Collectors.summingInt(CheckoutItem::quantity)));

        BigDecimal total = BigDecimal.ZERO;
        for (CheckoutItem checkoutItem : items) {
            Product product = productService.findById(checkoutItem.productId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + checkoutItem.productId()));

            int requestedQuantity = requestedQuantities.get(checkoutItem.productId());
            if (requestedQuantity > product.getStockQuantity()) {
                throw new InsufficientStockException(
                        "Insufficient stock for product " + product.getId() + ": requested "
                                + requestedQuantity + ", available " + product.getStockQuantity());
            }

            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(checkoutItem.quantity())));

            order.addItem(OrderItem.builder()
                    .productId(product.getId())
                    .quantity(checkoutItem.quantity())
                    .unitPrice(product.getPrice())
                    .build());
        }
        order.setTotalAmount(total);

        PaymentGateway.PaymentResult payment = paymentGateway.charge(order.getId(), total, "BRL");
        order.setStatus(payment.approved() ? OrderStatus.PAID : OrderStatus.FAILED);

        Order saved = orderRepository.save(order);

        if (payment.approved()) {
            eventPublisher.publishEvent(OrderPlacedEvent.from(saved));
        }

        return saved;
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
    public List<Order> findByProductIds(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        List<Long> orderIds = orderRepository.findOrderIdsByItemsProductIdIn(productIds);
        if (orderIds.isEmpty()) {
            return List.of();
        }
        return orderRepository.findByIdInWithItems(orderIds);
    }
}
