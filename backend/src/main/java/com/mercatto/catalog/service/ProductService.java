package com.mercatto.catalog.service;

import com.mercatto.catalog.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Public API of the Catalog module.
 */
public interface ProductService {

    Page<Product> search(String query, String category, Pageable pageable);

    Optional<Product> findById(Long id);

    Product create(Product product);

    Product update(Long id, Product changes);

    void delete(Long id);

    Page<Product> findBySeller(Long sellerId, Pageable pageable);

    /**
     * Returns only the ids of a seller's products, for cross-module
     * composition (e.g. Sellers looking up which orders contain one of its
     * products) — cheaper than {@link #findBySeller} and avoids exposing the
     * full {@link Product} entity/page to callers that only need ids.
     */
    List<Long> findProductIdsBySeller(Long sellerId);

    void decreaseStock(Long productId, int quantity);

    List<String> listCategories();
}
