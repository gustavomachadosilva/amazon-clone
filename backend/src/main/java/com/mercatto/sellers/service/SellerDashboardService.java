package com.mercatto.sellers.service;

import com.mercatto.catalog.service.ProductService;
import com.mercatto.orders.service.FulfillmentStatus;
import com.mercatto.orders.service.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Public API of the Sellers module. Sellers depends on Catalog's public
 * {@link com.mercatto.catalog.service.ProductService} and Orders' public
 * {@link com.mercatto.orders.service.OrderService} only — never on their
 * repositories or entities' persistence internals.
 */
public interface SellerDashboardService {

    record SellerOrderItemView(Long productId, int quantity, BigDecimal unitPrice) {}

    /**
     * A seller's own view of an order: only the line items that belong to
     * that seller, never another seller's items from the same buyer
     * checkout, and a subtotal computed from just those items (not the
     * order's overall total, which may include other sellers' revenue).
     */
    record SellerOrderView(Long orderId, Long buyerId, OrderStatus status, FulfillmentStatus fulfillmentStatus,
                            Instant createdAt, List<SellerOrderItemView> items, BigDecimal subtotal) {}

    record SellerMetricsView(BigDecimal totalRevenue, List<ProductService.ProductSummary> lowStockProducts) {}

    Page<ProductService.ProductSummary> getInventory(Long sellerId, Pageable pageable);

    List<SellerOrderView> getReceivedOrders(Long sellerId);

    SellerMetricsView getMetrics(Long sellerId);

    /**
     * Advances the fulfillment status of an order the seller has items in, by delegating to
     * Orders' public {@link com.mercatto.orders.service.OrderService#advanceFulfillment}. The
     * transition rules live in Orders; its exceptions pass through unchanged
     * ({@code IllegalArgumentException}, {@code OrderNotFoundException},
     * {@code OrderAccessDeniedException}, {@code InvalidFulfillmentTransitionException}).
     * The result is scoped to this seller's items, like {@link #getReceivedOrders}.
     */
    SellerOrderView advanceFulfillment(Long sellerId, Long orderId, FulfillmentStatus next);
}
