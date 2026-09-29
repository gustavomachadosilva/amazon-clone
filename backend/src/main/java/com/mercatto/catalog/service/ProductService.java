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
     * A {@link Product} with its aggregate rating. The rating is Catalog's denormalized copy of
     * the Reviews module's aggregate, refreshed by {@code ReviewRatingSyncListener} whenever a
     * review is created, so searches can filter and sort by it in SQL.
     */
    record ProductView(Long id, String name, String description, BigDecimal price, Integer stockQuantity,
                        String category, String imageUrl, String brand, Integer warrantyMonths,
                        String modelNumber, BigDecimal listPrice, Long sellerId, Instant createdAt,
                        double averageRating, long reviewCount) {}

    /**
     * One page of the products matching {@code criteria}, filtered and ordered in the database so
     * {@code totalElements}/{@code totalPages} describe the whole filtered result.
     */
    Page<ProductView> searchWithRating(ProductSearchCriteria criteria, int page, int size);

    Optional<ProductView> findByIdWithRating(Long id);

    /**
     * A product recommended as related to another, with its similarity score and the reasons it
     * was picked ({@code reasons} in {@link RelatedReason} priority order, {@code primaryReason}
     * being the first). Every reason is checked against the data, so it can be shown as-is.
     */
    record RelatedProduct(ProductView product, double score, RelatedReason primaryReason,
                          List<RelatedReason> reasons) {}

    /**
     * Up to {@code limit} in-stock products related to {@code productId}, most related first
     * (Card #223). Candidates come from the same category; only when that yields fewer than
     * {@code limit} are products from other categories considered, and then only those sharing
     * the brand or a similar name — the result is never padded with unrelated items, so it may be
     * shorter than {@code limit} or empty.
     *
     * @throws ProductNotFoundException when {@code productId} doesn't exist
     */
    List<RelatedProduct> findRelated(Long productId, int limit);
}
