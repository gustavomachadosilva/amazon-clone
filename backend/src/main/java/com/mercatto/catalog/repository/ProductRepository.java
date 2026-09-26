package com.mercatto.catalog.repository;

import com.mercatto.catalog.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Page<Product> findBySellerId(Long sellerId, Pageable pageable);

    @Query("select distinct p.category from Product p order by p.category")
    List<String> findDistinctCategories();

    // Bulk UPDATE is the only writer of the denormalized rating columns (they are
    // insertable/updatable=false on the entity). It deliberately does not bump @Version: a
    // rating refresh must not make a concurrent seller edit or stock decrement fail.
    @Modifying
    @Query("update Product p set p.averageRating = :averageRating, p.reviewCount = :reviewCount where p.id = :id")
    int updateRating(@Param("id") Long id,
                     @Param("averageRating") double averageRating,
                     @Param("reviewCount") long reviewCount);
}
