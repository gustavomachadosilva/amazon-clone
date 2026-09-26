package com.mercatto.catalog.service;

import com.mercatto.reviews.event.ReviewCreatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReviewRatingSyncListenerTest {

    @Mock
    private ProductRatingSynchronizer synchronizer;

    @Test
    void refreshesTheReviewedProductsRating() {
        ReviewRatingSyncListener listener = new ReviewRatingSyncListener(synchronizer);

        listener.onReviewCreated(new ReviewCreatedEvent(10L, 1L, 4));

        verify(synchronizer).refresh(1L);
    }

    @Test
    void swallowsRefreshFailuresSoTheCommittedReviewIsNotReportedAsFailed() {
        ReviewRatingSyncListener listener = new ReviewRatingSyncListener(synchronizer);
        doThrow(new IllegalStateException("db down")).when(synchronizer).refresh(1L);

        assertThatCode(() -> listener.onReviewCreated(new ReviewCreatedEvent(10L, 1L, 4)))
                .doesNotThrowAnyException();
        verify(synchronizer).refresh(1L);
    }
}
