package com.mercatto.catalog.service;

import com.mercatto.catalog.repository.ProductRepository;
import com.mercatto.reviews.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

/**
 * Copies the Reviews module's rating aggregate (read through its public {@link ReviewService})
 * onto Catalog's denormalized {@code Product.averageRating}/{@code reviewCount} columns. Always
 * writes the absolute, freshly recomputed values rather than applying an increment, so a
 * duplicated or missed event is healed by the next refresh (or by the boot-time backfill).
 *
 * <p>REQUIRES_NEW for the same reason as {@code ProductServiceImpl#decreaseStock}: it is called
 * from an AFTER_COMMIT listener, where a default-propagation transaction would silently join the
 * already-committed triggering transaction and the update would never be flushed.
 */
@Component
@RequiredArgsConstructor
class ProductRatingSynchronizer {

    private final ProductRepository productRepository;
    private final ReviewService reviewService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refresh(Long productId) {
        ReviewService.RatingAggregate aggregate = reviewService.getAggregate(productId);
        productRepository.updateRating(productId, aggregate.averageRating(), aggregate.reviewCount());
    }

    /**
     * Writes each given aggregate in one new transaction. Callers pass only the aggregates that
     * actually differ from what is stored, so unchanged rows aren't rewritten.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void apply(Collection<ReviewService.RatingAggregate> aggregates) {
        for (ReviewService.RatingAggregate aggregate : aggregates) {
            productRepository.updateRating(aggregate.productId(), aggregate.averageRating(), aggregate.reviewCount());
        }
    }
}
