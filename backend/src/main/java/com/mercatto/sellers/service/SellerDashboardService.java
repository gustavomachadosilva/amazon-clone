package com.mercatto.sellers.service;

import com.mercatto.catalog.domain.Product;
import com.mercatto.orders.domain.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Public API of the Sellers module. Sellers depends on Catalog's public
 * {@link com.mercatto.catalog.service.ProductService} and Orders' public
 * {@link com.mercatto.orders.service.OrderService} only — never on their
 * repositories or entities' persistence internals.
 */
public interface SellerDashboardService {
    Page<Product> getInventory(Long sellerId, Pageable pageable);

    List<Order> getReceivedOrders(Long sellerId);
}
