package com.mercatto.catalog.service;

import com.mercatto.catalog.domain.Product;
import com.mercatto.catalog.repository.ProductRepository;
import com.mercatto.reviews.service.ReviewService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductRatingBackfillTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ReviewService reviewService;

    @Mock
    private ProductRatingSynchronizer synchronizer;

    @InjectMocks
    private ProductRatingBackfill backfill;

    private static Product product(long id, double averageRating, long reviewCount) {
        return Product.builder().id(id).averageRating(averageRating).reviewCount(reviewCount).build();
    }

    @Test
    void updatesOnlyProductsWhoseStoredRatingDiffers() {
        Pageable first = PageRequest.of(0, ProductRatingBackfill.PAGE_SIZE, Sort.by("id"));
        when(productRepository.findAll(first)).thenReturn(new PageImpl<>(
                List.of(product(1L, 4.0, 1L), product(2L, 0.0, 0L), product(3L, 0.0, 0L)), first, 3));
        when(reviewService.getAggregates(List.of(1L, 2L, 3L))).thenReturn(Map.of(
                1L, new ReviewService.RatingAggregate(1L, 4.0, 1L),
                2L, new ReviewService.RatingAggregate(2L, 3.5, 2L),
                3L, ReviewService.RatingAggregate.empty(3L)));

        backfill.backfillRatings();

        verify(synchronizer).apply(List.of(new ReviewService.RatingAggregate(2L, 3.5, 2L)));
    }

    @Test
    void walksEveryPage() {
        Pageable first = PageRequest.of(0, ProductRatingBackfill.PAGE_SIZE, Sort.by("id"));
        Pageable second = first.next();
        long total = ProductRatingBackfill.PAGE_SIZE + 1L;
        when(productRepository.findAll(first)).thenReturn(new PageImpl<>(List.of(product(1L, 0.0, 0L)), first, total));
        when(productRepository.findAll(second)).thenReturn(new PageImpl<>(List.of(product(2L, 0.0, 0L)), second, total));
        when(reviewService.getAggregates(List.of(1L))).thenReturn(Map.of(
                1L, new ReviewService.RatingAggregate(1L, 5.0, 1L)));
        when(reviewService.getAggregates(List.of(2L))).thenReturn(Map.of(
                2L, new ReviewService.RatingAggregate(2L, 2.0, 1L)));

        backfill.backfillRatings();

        verify(synchronizer).apply(List.of(new ReviewService.RatingAggregate(1L, 5.0, 1L)));
        verify(synchronizer).apply(List.of(new ReviewService.RatingAggregate(2L, 2.0, 1L)));
    }

    @Test
    void doesNothingWhenEverythingIsInSync() {
        Pageable first = PageRequest.of(0, ProductRatingBackfill.PAGE_SIZE, Sort.by("id"));
        when(productRepository.findAll(first)).thenReturn(new PageImpl<>(List.of(product(1L, 0.0, 0L)), first, 1));
        when(reviewService.getAggregates(List.of(1L))).thenReturn(Map.of());

        backfill.backfillRatings();

        verify(synchronizer, never()).apply(any());
    }

    @Test
    void neverFailsTheBoot() {
        when(productRepository.findAll(any(Pageable.class))).thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> backfill.backfillRatings()).doesNotThrowAnyException();
    }
}
