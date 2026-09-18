package com.mercatto.reviews.service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Public API of the Reviews module.
 */
public interface ReviewService {

    record RatingAggregate(Long productId, double averageRating, long reviewCount) {

        public static RatingAggregate empty(Long productId) {
            return new RatingAggregate(productId, 0.0, 0L);
        }
    }

    record ReviewView(Long id, Long productId, Long authorId, String authorName, int stars, String title,
                       String text, long helpfulCount, Instant createdAt) {}

    ReviewView createReview(Long productId, Long authorId, int stars, String title, String text);

    List<ReviewView> listByProduct(Long productId);

    ReviewView markHelpful(Long reviewId);

    RatingAggregate getAggregate(Long productId);

    Map<Long, RatingAggregate> getAggregates(Collection<Long> productIds);
}
