package com.mercatto.catalog.repository;

import com.mercatto.catalog.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Page<Product> findBySellerId(Long sellerId, Pageable pageable);

    @Query("select distinct p.category from Product p order by p.category")
    List<String> findDistinctCategories();

    /**
     * Typo correction for the search (#220): the catalog word (from name, brand, category and
     * description, unaccented and lowercased) closest to {@code term}, within {@code maxEdits}
     * Levenshtein edits and with trigram similarity ≥ 0.45; ties go to the word in more products.
     * Returns {@code term} itself when it is already a catalog word. Scans the whole vocabulary
     * ({@code ts_stat}), so it's only called when a search found nothing.
     */
    @Query(nativeQuery = true, value = """
            SELECT w.word
            FROM ts_stat('SELECT to_tsvector(''simple'', catalog.product_search_text(name, brand, category, description)) FROM catalog.products') w
            WHERE public.levenshtein(w.word, catalog.immutable_unaccent(:term)) <= :maxEdits
              AND public.similarity(w.word, catalog.immutable_unaccent(:term)) >= 0.45
            ORDER BY public.levenshtein(w.word, catalog.immutable_unaccent(:term)),
                     public.similarity(w.word, catalog.immutable_unaccent(:term)) DESC,
                     w.ndoc DESC,
                     w.word
            LIMIT 1
            """)
    Optional<String> findClosestIndexedWord(@Param("term") String term, @Param("maxEdits") int maxEdits);

    /** {@code ANALYZE} of the products table, for right after a bulk load (dev seed). */
    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = "ANALYZE catalog.products")
    void refreshStatistics();

    // Candidate pools for related products (ProductServiceImpl#findRelated). Capped so a huge
    // category can't turn a product page into a full-table scan.
    List<Product> findTop200ByCategoryAndIdNotAndStockQuantityGreaterThan(String category, Long id, int stock);

    List<Product> findTop50ByBrandIgnoreCaseAndCategoryNotAndStockQuantityGreaterThan(String brand, String category,
                                                                                      int stock);

    /** Best average rating among the reviewed products of {@code category}, or {@code null} if none is reviewed. */
    @Query("select max(p.averageRating) from Product p where p.category = :category and p.reviewCount > 0")
    Double findTopAverageRatingInCategory(@Param("category") String category);

    // Bulk UPDATE is the only writer of the denormalized rating columns (they are
    // insertable/updatable=false on the entity). It deliberately does not bump @Version: a
    // rating refresh must not make a concurrent seller edit or stock decrement fail.
    @Modifying
    @Query("update Product p set p.averageRating = :averageRating, p.reviewCount = :reviewCount where p.id = :id")
    int updateRating(@Param("id") Long id,
                     @Param("averageRating") double averageRating,
                     @Param("reviewCount") long reviewCount);
}
