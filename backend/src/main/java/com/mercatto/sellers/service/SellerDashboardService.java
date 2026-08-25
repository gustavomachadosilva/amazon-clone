package com.mercatto.sellers.service;

import com.mercatto.catalog.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Public API of the Sellers module. Sellers depends on Catalog's public
 * {@link com.mercatto.catalog.service.ProductService} only — never on its
 * repository or entities' persistence internals.
 */
public interface SellerDashboardService {
    Page<Product> getInventory(Long sellerId, Pageable pageable);
}
