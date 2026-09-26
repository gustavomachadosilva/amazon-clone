package com.mercatto.catalog.service;

import com.mercatto.catalog.repository.ProductRepository;
import com.mercatto.reviews.service.ReviewService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductRatingSynchronizerTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ReviewService reviewService;

    @InjectMocks
    private ProductRatingSynchronizer synchronizer;

    @Test
    void refreshWritesTheRecomputedAggregate() {
        when(reviewService.getAggregate(1L)).thenReturn(new ReviewService.RatingAggregate(1L, 4.5, 2L));

        synchronizer.refresh(1L);

        verify(productRepository).updateRating(1L, 4.5, 2L);
    }

    @Test
    void refreshWritesZeroWhenTheProductHasNoReviews() {
        when(reviewService.getAggregate(1L)).thenReturn(ReviewService.RatingAggregate.empty(1L));

        synchronizer.refresh(1L);

        verify(productRepository).updateRating(1L, 0.0, 0L);
    }

    @Test
    void applyWritesEachGivenAggregate() {
        synchronizer.apply(List.of(
                new ReviewService.RatingAggregate(1L, 4.0, 1L),
                new ReviewService.RatingAggregate(2L, 2.5, 2L)));

        verify(productRepository).updateRating(1L, 4.0, 1L);
        verify(productRepository).updateRating(2L, 2.5, 2L);
        verifyNoInteractions(reviewService);
    }
}
