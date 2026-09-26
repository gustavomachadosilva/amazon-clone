package com.mercatto.catalog.service;

import com.mercatto.catalog.domain.Product;
import com.mercatto.catalog.repository.ProductRepository;
import com.mercatto.reviews.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reconciles every product's denormalized rating with the Reviews module's aggregates, for rows
 * written before the columns existed (they start at the 0 default) or whose
 * {@link ReviewRatingSyncListener} refresh failed. Runs on every boot, a page of products at a
 * time, and only rewrites the rows whose stored values differ, so it is a cheap no-op once
 * everything is in sync. Reads Reviews only through its public {@link ReviewService}.
 */
@Component
@Slf4j
@RequiredArgsConstructor
class ProductRatingBackfill {

    static final int PAGE_SIZE = 500;

    private final ProductRepository productRepository;
    private final ReviewService reviewService;
    private final ProductRatingSynchronizer synchronizer;

    @EventListener(ApplicationReadyEvent.class)
    public void backfillRatings() {
        try {
            int updated = backfillAllPages();
            if (updated > 0) {
                log.info("Backfilled rating on {} products", updated);
            }
        } catch (Exception ex) {
            // A stale rating is not worth failing the boot over; the next boot retries.
            log.error("Failed to backfill product ratings", ex);
        }
    }

    private int backfillAllPages() {
        int updated = 0;
        Pageable pageable = PageRequest.of(0, PAGE_SIZE, Sort.by("id"));
        Page<Product> page;
        do {
            page = productRepository.findAll(pageable);
            List<Long> ids = page.getContent().stream().map(Product::getId).toList();
            if (ids.isEmpty()) {
                break;
            }
            Map<Long, ReviewService.RatingAggregate> aggregates = reviewService.getAggregates(ids);
            List<ReviewService.RatingAggregate> changed = new ArrayList<>();
            for (Product product : page.getContent()) {
                ReviewService.RatingAggregate aggregate = aggregates.getOrDefault(
                        product.getId(), ReviewService.RatingAggregate.empty(product.getId()));
                if (differs(product, aggregate)) {
                    changed.add(aggregate);
                }
            }
            if (!changed.isEmpty()) {
                synchronizer.apply(changed);
                updated += changed.size();
            }
            pageable = page.nextPageable();
        } while (page.hasNext());
        return updated;
    }

    private static boolean differs(Product product, ReviewService.RatingAggregate aggregate) {
        return !Objects.equals(product.getReviewCount(), aggregate.reviewCount())
                || product.getAverageRating() == null
                || Double.compare(product.getAverageRating(), aggregate.averageRating()) != 0;
    }
}
