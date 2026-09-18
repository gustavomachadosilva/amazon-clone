package com.mercatto.reviews.service;

import com.mercatto.reviews.domain.Review;
import com.mercatto.reviews.repository.ReviewRepository;
import com.mercatto.users.domain.User;
import com.mercatto.users.domain.UserRole;
import com.mercatto.users.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    private static User user(long id, String name) {
        return User.builder().id(id).name(name).email(name + "@example.com").role(UserRole.BUYER).build();
    }

    private static Review review(long id, long productId, long authorId, int stars, int helpfulCount) {
        return Review.builder()
                .id(id)
                .productId(productId)
                .authorId(authorId)
                .stars(stars)
                .title("Title " + id)
                .text("Text " + id)
                .helpfulCount(helpfulCount)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }

    @Test
    void createReviewSavesAndReturnsViewWithResolvedAuthorName() {
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review r = invocation.getArgument(0);
            r.setId(1L);
            r.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
            return r;
        });
        when(userService.findById(10L)).thenReturn(Optional.of(user(10L, "Ana")));

        ReviewService.ReviewView view = reviewService.createReview(5L, 10L, 4, "Great", "Loved it");

        assertThat(view.id()).isEqualTo(1L);
        assertThat(view.productId()).isEqualTo(5L);
        assertThat(view.authorId()).isEqualTo(10L);
        assertThat(view.authorName()).isEqualTo("Ana");
        assertThat(view.stars()).isEqualTo(4);
        assertThat(view.title()).isEqualTo("Great");
        assertThat(view.text()).isEqualTo("Loved it");
        assertThat(view.helpfulCount()).isEqualTo(0);

        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(captor.capture());
        assertThat(captor.getValue().getProductId()).isEqualTo(5L);
        assertThat(captor.getValue().getAuthorId()).isEqualTo(10L);
        assertThat(captor.getValue().getStars()).isEqualTo(4);
    }

    @Test
    void createReviewWithStarsBelowRangeThrows() {
        assertThatThrownBy(() -> reviewService.createReview(5L, 10L, 0, "Title", "Text"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createReviewWithStarsAboveRangeThrows() {
        assertThatThrownBy(() -> reviewService.createReview(5L, 10L, 6, "Title", "Text"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void listByProductMapsReviewsIncludingAuthorName() {
        Review r1 = review(1L, 5L, 10L, 5, 2);
        Review r2 = review(2L, 5L, 20L, 3, 0);
        when(reviewRepository.findByProductIdOrderByCreatedAtDesc(5L)).thenReturn(List.of(r1, r2));
        when(userService.findById(10L)).thenReturn(Optional.of(user(10L, "Ana")));
        when(userService.findById(20L)).thenReturn(Optional.empty());

        List<ReviewService.ReviewView> views = reviewService.listByProduct(5L);

        assertThat(views).hasSize(2);
        assertThat(views.get(0).authorName()).isEqualTo("Ana");
        // Fallback when the author lookup fails/returns empty.
        assertThat(views.get(1).authorName()).isEqualTo("Usuário");
    }

    @Test
    void markHelpfulIncrementsCount() {
        Review r = review(1L, 5L, 10L, 5, 2);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(r));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userService.findById(10L)).thenReturn(Optional.of(user(10L, "Ana")));

        ReviewService.ReviewView view = reviewService.markHelpful(1L);

        assertThat(view.helpfulCount()).isEqualTo(3);
        verify(reviewRepository).save(r);
    }

    @Test
    void markHelpfulThrowsForUnknownId() {
        when(reviewRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.markHelpful(99L))
                .isInstanceOf(ReviewNotFoundException.class);

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void getAggregateReturnsEmptyForZeroReviews() {
        when(reviewRepository.aggregateForProduct(5L)).thenReturn(null);

        ReviewService.RatingAggregate aggregate = reviewService.getAggregate(5L);

        assertThat(aggregate.productId()).isEqualTo(5L);
        assertThat(aggregate.averageRating()).isEqualTo(0.0);
        assertThat(aggregate.reviewCount()).isEqualTo(0L);
    }

    @Test
    void getAggregateReturnsComputedValuesWhenReviewsExist() {
        ReviewRepository.RatingAggregateProjection projection = projection(4.5, 2L);
        when(reviewRepository.aggregateForProduct(5L)).thenReturn(projection);

        ReviewService.RatingAggregate aggregate = reviewService.getAggregate(5L);

        assertThat(aggregate.productId()).isEqualTo(5L);
        assertThat(aggregate.averageRating()).isEqualTo(4.5);
        assertThat(aggregate.reviewCount()).isEqualTo(2L);
    }

    @Test
    void getAggregatesReturnsEmptyAggregateForProductsWithoutReviewsAndComputedForOthers() {
        ReviewRepository.ProductRatingAggregateProjection projection = productProjection(1L, 5.0, 3L);
        when(reviewRepository.aggregateForProducts(List.of(1L, 2L))).thenReturn(List.of(projection));

        Map<Long, ReviewService.RatingAggregate> aggregates = reviewService.getAggregates(List.of(1L, 2L));

        assertThat(aggregates.get(1L).averageRating()).isEqualTo(5.0);
        assertThat(aggregates.get(1L).reviewCount()).isEqualTo(3L);
        assertThat(aggregates.get(2L)).isEqualTo(ReviewService.RatingAggregate.empty(2L));
    }

    @Test
    void getAggregatesReturnsEmptyMapForEmptyInput() {
        Map<Long, ReviewService.RatingAggregate> aggregates = reviewService.getAggregates(List.of());

        assertThat(aggregates).isEmpty();
    }

    private static ReviewRepository.RatingAggregateProjection projection(Double avg, Long count) {
        return new ReviewRepository.RatingAggregateProjection() {
            @Override
            public Double getAverageRating() {
                return avg;
            }

            @Override
            public Long getReviewCount() {
                return count;
            }
        };
    }

    private static ReviewRepository.ProductRatingAggregateProjection productProjection(Long productId, Double avg, Long count) {
        return new ReviewRepository.ProductRatingAggregateProjection() {
            @Override
            public Long getProductId() {
                return productId;
            }

            @Override
            public Double getAverageRating() {
                return avg;
            }

            @Override
            public Long getReviewCount() {
                return count;
            }
        };
    }
}
