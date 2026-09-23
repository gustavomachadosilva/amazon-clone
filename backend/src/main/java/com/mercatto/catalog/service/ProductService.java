package com.mercatto.catalog.service;

import com.mercatto.catalog.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Public API of the Catalog module.
 */
public interface ProductService {

    Page<Product> search(String query, String category, Pageable pageable);

    /**
     * A persistence-free read model of a {@link Product}, used for every cross-module read
     * (cart, orders, sellers) so those modules never depend on catalog's JPA entity shape
     * (Contrato de Modularidade regra 3 / Card #142). Not reused for {@link #searchWithRating}/
     * {@link #findByIdWithRating} — those already have their own read model ({@link ProductView})
     * carrying the rating fields.
     */
    record ProductSummary(Long id, String name, String description, BigDecimal price, Integer stockQuantity,
                           String category, String imageUrl, String brand, Integer warrantyMonths,
                           String modelNumber, BigDecimal listPrice, Long sellerId, Instant createdAt) {}

    Optional<ProductSummary> findById(Long id);

    Product create(Product product);

    Product update(Long id, Product changes);

    void delete(Long id);

    Page<ProductSummary> findBySeller(Long sellerId, Pageable pageable);

    void decreaseStock(Long productId, int quantity);

    List<String> listCategories();

    /**
     * A {@link Product} enriched with its aggregate rating, resolved read-only from the
     * Reviews module. Additive read model: existing {@link #search}/{@link #findById}
     * callers (cart, sellers, orders) keep returning the plain {@link Product} untouched.
     */
    record ProductView(Long id, String name, String description, BigDecimal price, Integer stockQuantity,
                        String category, String imageUrl, String brand, Integer warrantyMonths,
                        String modelNumber, BigDecimal listPrice, Long sellerId, Instant createdAt,
                        double averageRating, long reviewCount) {}

    Page<ProductView> searchWithRating(String query, String category, Pageable pageable);

    Optional<ProductView> findByIdWithRating(Long id);
}
