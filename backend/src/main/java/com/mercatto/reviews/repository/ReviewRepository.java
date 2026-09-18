package com.mercatto.reviews.repository;

import com.mercatto.reviews.domain.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByProductIdOrderByCreatedAtDesc(Long productId);

    // Atomic increment (not read-then-save) so concurrent "mark helpful" clicks on the
    // same review never lose an update — same compare-and-swap-by-UPDATE idiom as
    // OrderRepository.claimForCharging.
    @Modifying
    @Query("update Review r set r.helpfulCount = r.helpfulCount + 1 where r.id = :id")
    int incrementHelpfulCount(@Param("id") Long id);

    @Query("""
            select avg(r.stars) as averageRating, count(r) as reviewCount
            from Review r
            where r.productId = :productId
            """)
    RatingAggregateProjection aggregateForProduct(@Param("productId") Long productId);

    // Batch variant used by Catalog's search/listing pages so a page of N products costs one
    // query instead of N (avoids N+1 when rendering rating badges on a results grid).
    @Query("""
            select r.productId as productId, avg(r.stars) as averageRating, count(r) as reviewCount
            from Review r
            where r.productId in :productIds
            group by r.productId
            """)
    List<ProductRatingAggregateProjection> aggregateForProducts(@Param("productIds") Collection<Long> productIds);

    interface RatingAggregateProjection {
        Double getAverageRating();

        Long getReviewCount();
    }

    interface ProductRatingAggregateProjection {
        Long getProductId();

        Double getAverageRating();

        Long getReviewCount();
    }
}
