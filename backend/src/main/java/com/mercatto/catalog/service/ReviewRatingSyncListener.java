package com.mercatto.catalog.service;

import com.mercatto.reviews.event.ReviewCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reacts to the Reviews module's {@link ReviewCreatedEvent} by refreshing the product's
 * denormalized rating. Runs after the review's transaction commits, so a failure here never
 * rolls back the review; the stale rating is corrected by the next review of that product or by
 * {@link ProductRatingBackfill} on the next boot.
 */
@Component
@Slf4j
@RequiredArgsConstructor
class ReviewRatingSyncListener {

    private final ProductRatingSynchronizer synchronizer;

    // fallbackExecution: the JSON review endpoint reaches ReviewServiceImpl through the
    // ReviewService default 5-arg createReview, which self-invokes the @Transactional overload
    // and so runs without a transaction (each save auto-commits). Without this flag Spring would
    // silently drop the event there; with it the listener runs right away, after the review row
    // is already committed.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onReviewCreated(ReviewCreatedEvent event) {
        try {
            synchronizer.refresh(event.productId());
        } catch (Exception ex) {
            // Must not escape this AFTER_COMMIT callback and surface as a 500 on an
            // already-committed review.
            log.error("Failed to refresh rating of product {} after review {} (review already saved)",
                    event.productId(), event.reviewId(), ex);
        }
    }
}
