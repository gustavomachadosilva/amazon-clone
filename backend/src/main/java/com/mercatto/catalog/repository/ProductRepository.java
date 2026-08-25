package com.mercatto.catalog.repository;

import com.mercatto.catalog.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("""
            select p from Product p
            where (:query is null or lower(p.name) like lower(concat('%', :query, '%')))
              and (:category is null or p.category = :category)
            """)
    Page<Product> search(@Param("query") String query, @Param("category") String category, Pageable pageable);

    Page<Product> findBySellerId(Long sellerId, Pageable pageable);
}
