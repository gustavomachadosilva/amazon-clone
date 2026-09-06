package com.mercatto.sellers.service;

import com.mercatto.catalog.domain.Product;
import com.mercatto.orders.domain.OrderStatus;
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
    record SellerOrderView(Long orderId, Long buyerId, OrderStatus status, Instant createdAt,
                            List<SellerOrderItemView> items, BigDecimal subtotal) {}

    Page<Product> getInventory(Long sellerId, Pageable pageable);

    List<SellerOrderView> getReceivedOrders(Long sellerId);
}
