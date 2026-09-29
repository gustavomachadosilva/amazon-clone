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
     *
     * <p>The free-text {@code query} (#220) is matched against name, brand, category and
     * description. Every term must match (in any field, in any order); matching ignores case and
     * accents, stems English words ("laptops" finds "Laptop") and treats terms of 3+ characters as
     * prefixes. A term containing {@code %} or {@code _} is matched as literal text. Only when a
     * search finds nothing, each word of 4+ letters that isn't in the catalog is replaced by the
     * closest catalog word (1 edit, 2 from 8 letters) and the search runs once more with the same
     * filters. With {@link ProductSort#RELEVANCE}, results are ordered by full-text rank.
     */
    Page<ProductView> searchWithRating(ProductSearchCriteria criteria, int page, int size);

    Optional<ProductView> findByIdWithRating(Long id);
}
