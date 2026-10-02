package com.mercatto.reviews.service;

import com.mercatto.reviews.domain.Review;
import com.mercatto.reviews.domain.ReviewMedia;
import com.mercatto.reviews.domain.ReviewMediaType;
import com.mercatto.reviews.event.ReviewCreatedEvent;
import com.mercatto.reviews.repository.ReviewMediaRepository;
import com.mercatto.reviews.repository.ReviewRepository;
import com.mercatto.users.domain.User;
import com.mercatto.users.domain.UserRole;
import com.mercatto.users.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewMediaRepository reviewMediaRepository;

    @Mock
    private ReviewMediaStorage reviewMediaStorage;

    @Mock
    private UserService userService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

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
    void createReviewPublishesReviewCreatedEventForCatalogRatingSync() {
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review r = invocation.getArgument(0);
            r.setId(1L);
            return r;
        });

        reviewService.createReview(5L, 10L, 4, "Great", "Loved it");

        verify(eventPublisher).publishEvent(new ReviewCreatedEvent(1L, 5L, 4));
    }

    @Test
    void createReviewWithStarsBelowRangeThrows() {
        assertThatThrownBy(() -> reviewService.createReview(5L, 10L, 0, "Title", "Text"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(reviewRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void createReviewWithStarsAboveRangeThrows() {
        assertThatThrownBy(() -> reviewService.createReview(5L, 10L, 6, "Title", "Text"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(reviewRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
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
        Review r = review(1L, 5L, 10L, 5, 3);
        when(reviewRepository.incrementHelpfulCount(1L)).thenReturn(1);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(r));
        when(userService.findById(10L)).thenReturn(Optional.of(user(10L, "Ana")));

        ReviewService.ReviewView view = reviewService.markHelpful(1L);

        assertThat(view.helpfulCount()).isEqualTo(3);
        verify(reviewRepository).incrementHelpfulCount(1L);
    }

    @Test
    void markHelpfulThrowsForUnknownId() {
        when(reviewRepository.incrementHelpfulCount(99L)).thenReturn(0);

        assertThatThrownBy(() -> reviewService.markHelpful(99L))
                .isInstanceOf(ReviewNotFoundException.class);
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

    // --- review media ------------------------------------------------------------------------

    private static final long MB = 1024L * 1024;

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static byte[] jpeg() {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;
        return bytes;
    }

    private static byte[] mp4() {
        byte[] bytes = new byte[32];
        System.arraycopy("ftypisom".getBytes(StandardCharsets.US_ASCII), 0, bytes, 4, 8);
        return bytes;
    }

    private static ReviewService.MediaUpload upload(byte[] bytes) {
        return new ReviewService.MediaUpload(new ByteArrayResource(bytes), bytes.length);
    }

    private static ReviewService.MediaUpload upload(byte[] bytes, long declaredSize) {
        return new ReviewService.MediaUpload(new ByteArrayResource(bytes), declaredSize);
    }

    private static ReviewMedia media(long id, long reviewId, ReviewMediaType type, int position) {
        return ReviewMedia.builder()
                .id(id)
                .reviewId(reviewId)
                .type(type)
                .contentType(type == ReviewMediaType.IMAGE ? "image/jpeg" : "video/mp4")
                .sizeBytes(32)
                .storageKey("key-" + id)
                .position(position)
                .build();
    }

    private void stubSaves() {
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review r = invocation.getArgument(0);
            r.setId(1L);
            r.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
            return r;
        });
    }

    private void stubMediaSaveAll() {
        AtomicInteger ids = new AtomicInteger(100);
        when(reviewMediaRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<ReviewMedia> rows = invocation.getArgument(0);
            rows.forEach(row -> row.setId((long) ids.getAndIncrement()));
            return rows;
        });
    }

    private void stubStoreWithSequentialKeys() throws IOException {
        AtomicInteger keys = new AtomicInteger();
        when(reviewMediaStorage.store(any(InputStream.class)))
                .thenAnswer(invocation -> "key-" + keys.getAndIncrement());
    }

    @Test
    void createReviewWithoutMediaDoesNotTouchStorage() {
        stubSaves();

        ReviewService.ReviewView view = reviewService.createReview(5L, 10L, 4, "Great", "Loved it", List.of());

        assertThat(view.media()).isEmpty();
        verifyNoInteractions(reviewMediaStorage);
        verify(reviewMediaRepository, never()).saveAll(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void createReviewWithOneImageStoresItAndReturnsMediaView() throws IOException {
        stubSaves();
        stubMediaSaveAll();
        stubStoreWithSequentialKeys();

        ReviewService.ReviewView view = reviewService.createReview(5L, 10L, 4, "Great", "Loved it",
                List.of(upload(jpeg())));

        assertThat(view.media()).containsExactly(
                new ReviewService.MediaView(100L, "IMAGE", "/api/reviews/media/100"));

        ArgumentCaptor<List<ReviewMedia>> captor = ArgumentCaptor.forClass(List.class);
        verify(reviewMediaRepository).saveAll(captor.capture());
        ReviewMedia row = captor.getValue().get(0);
        assertThat(row.getReviewId()).isEqualTo(1L);
        assertThat(row.getContentType()).isEqualTo("image/jpeg");
        assertThat(row.getStorageKey()).isEqualTo("key-0");
        assertThat(row.getSizeBytes()).isEqualTo(32L);
        assertThat(row.getPosition()).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void createReviewWithFiveMixedFilesStoresAllInOrder() throws IOException {
        stubSaves();
        stubMediaSaveAll();
        stubStoreWithSequentialKeys();

        List<ReviewService.MediaUpload> uploads = List.of(
                upload(jpeg()), upload(mp4()), upload(jpeg()), upload(jpeg()), upload(mp4()));

        ReviewService.ReviewView view = reviewService.createReview(5L, 10L, 4, "Great", "Loved it", uploads);

        verify(reviewMediaStorage, times(5)).store(any(InputStream.class));
        assertThat(view.media()).extracting(ReviewService.MediaView::type)
                .containsExactly("IMAGE", "VIDEO", "IMAGE", "IMAGE", "VIDEO");
        assertThat(view.media()).extracting(ReviewService.MediaView::id)
                .containsExactly(100L, 101L, 102L, 103L, 104L);

        ArgumentCaptor<List<ReviewMedia>> captor = ArgumentCaptor.forClass(List.class);
        verify(reviewMediaRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(ReviewMedia::getPosition).containsExactly(0, 1, 2, 3, 4);
        assertThat(captor.getValue()).extracting(ReviewMedia::getStorageKey)
                .containsExactly("key-0", "key-1", "key-2", "key-3", "key-4");
        assertThat(captor.getValue().get(1).getContentType()).isEqualTo("video/mp4");
    }

    @Test
    void createReviewWithSixFilesIsRejectedBeforeTouchingStorage() {
        List<ReviewService.MediaUpload> uploads = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            uploads.add(upload(jpeg()));
        }

        assertThatThrownBy(() -> reviewService.createReview(5L, 10L, 4, "Great", "Loved it", uploads))
                .isInstanceOf(InvalidReviewMediaException.class)
                .hasMessage("A review can have at most 5 photos or videos");

        verifyNoInteractions(reviewMediaStorage);
        verify(reviewRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void createReviewWithDisguisedFileIsRejectedBeforeTouchingStorage() {
        // e.g. an SVG renamed to photo.jpg: its bytes, not its name, decide.
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes(StandardCharsets.US_ASCII);

        assertThatThrownBy(() -> reviewService.createReview(5L, 10L, 4, "Great", "Loved it",
                List.of(upload(jpeg()), upload(svg))))
                .isInstanceOf(InvalidReviewMediaException.class)
                .hasMessage("Only JPEG, PNG, WebP, MP4 or WebM files are allowed");

        verifyNoInteractions(reviewMediaStorage);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createReviewWithOversizedImageIsRejected() {
        assertThatThrownBy(() -> reviewService.createReview(5L, 10L, 4, "Great", "Loved it",
                List.of(upload(jpeg(), 5 * MB + 1))))
                .isInstanceOf(InvalidReviewMediaException.class)
                .hasMessage("Images must be at most 5 MB");

        verifyNoInteractions(reviewMediaStorage);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createReviewWithOversizedVideoIsRejected() {
        assertThatThrownBy(() -> reviewService.createReview(5L, 10L, 4, "Great", "Loved it",
                List.of(upload(mp4(), 50 * MB + 1))))
                .isInstanceOf(InvalidReviewMediaException.class)
                .hasMessage("Videos must be at most 50 MB");

        verifyNoInteractions(reviewMediaStorage);
    }

    @Test
    void createReviewAcceptsVideoAboveImageLimit() throws IOException {
        stubSaves();
        stubMediaSaveAll();
        stubStoreWithSequentialKeys();

        ReviewService.ReviewView view = reviewService.createReview(5L, 10L, 4, "Great", "Loved it",
                List.of(upload(mp4(), 20 * MB)));

        assertThat(view.media()).hasSize(1);
    }

    @Test
    void createReviewWithEmptyFileIsRejected() {
        assertThatThrownBy(() -> reviewService.createReview(5L, 10L, 4, "Great", "Loved it",
                List.of(upload(new byte[0]))))
                .isInstanceOf(InvalidReviewMediaException.class);

        verifyNoInteractions(reviewMediaStorage);
    }

    @Test
    void createReviewDeletesStoredFilesWhenReviewSaveFails() throws IOException {
        stubStoreWithSequentialKeys();
        when(reviewRepository.save(any(Review.class))).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> reviewService.createReview(5L, 10L, 4, "Great", "Loved it",
                List.of(upload(jpeg()), upload(mp4()))))
                .hasMessage("db down");

        verify(reviewMediaStorage).delete("key-0");
        verify(reviewMediaStorage).delete("key-1");
    }

    @Test
    void createReviewDeletesStoredFilesWhenMediaSaveFails() throws IOException {
        stubSaves();
        stubStoreWithSequentialKeys();
        when(reviewMediaRepository.saveAll(anyList())).thenThrow(new RuntimeException("constraint"));

        assertThatThrownBy(() -> reviewService.createReview(5L, 10L, 4, "Great", "Loved it",
                List.of(upload(jpeg()))))
                .hasMessage("constraint");

        verify(reviewMediaStorage).delete("key-0");
    }

    @Test
    void createReviewDeletesAlreadyStoredFilesWhenStoringAFileFails() throws IOException {
        when(reviewMediaStorage.store(any(InputStream.class)))
                .thenReturn("key-0", "key-1")
                .thenThrow(new IOException("disk full"));

        assertThatThrownBy(() -> reviewService.createReview(5L, 10L, 4, "Great", "Loved it",
                List.of(upload(jpeg()), upload(jpeg()), upload(jpeg()))))
                .hasRootCauseMessage("disk full");

        verify(reviewMediaStorage).delete("key-0");
        verify(reviewMediaStorage).delete("key-1");
        verify(reviewMediaStorage, times(2)).delete(anyString());
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createReviewDeletesStoredFilesWhenTransactionRollsBack() throws IOException {
        TransactionSynchronizationManager.initSynchronization();
        stubSaves();
        stubMediaSaveAll();
        stubStoreWithSequentialKeys();

        reviewService.createReview(5L, 10L, 4, "Great", "Loved it", List.of(upload(jpeg()), upload(mp4())));
        verify(reviewMediaStorage, never()).delete(anyString());

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);
        synchronizations.get(0).afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(reviewMediaStorage).delete("key-0");
        verify(reviewMediaStorage).delete("key-1");
    }

    @Test
    void createReviewKeepsStoredFilesWhenTransactionCommits() throws IOException {
        TransactionSynchronizationManager.initSynchronization();
        stubSaves();
        stubMediaSaveAll();
        stubStoreWithSequentialKeys();

        reviewService.createReview(5L, 10L, 4, "Great", "Loved it", List.of(upload(jpeg())));
        TransactionSynchronizationManager.getSynchronizations().get(0)
                .afterCompletion(TransactionSynchronization.STATUS_COMMITTED);

        verify(reviewMediaStorage, never()).delete(anyString());
    }

    @Test
    void listByProductLoadsMediaForAllReviewsInOneBatchQuery() {
        Review r1 = review(1L, 5L, 10L, 5, 0);
        Review r2 = review(2L, 5L, 10L, 3, 0);
        Review r3 = review(3L, 5L, 10L, 4, 0);
        when(reviewRepository.findByProductIdOrderByCreatedAtDesc(5L)).thenReturn(List.of(r1, r2, r3));
        when(userService.findById(10L)).thenReturn(Optional.of(user(10L, "Ana")));
        when(reviewMediaRepository.findByReviewIdInOrderByReviewIdAscPositionAsc(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(
                        media(11L, 1L, ReviewMediaType.IMAGE, 0),
                        media(12L, 1L, ReviewMediaType.VIDEO, 1),
                        media(31L, 3L, ReviewMediaType.IMAGE, 0)));

        List<ReviewService.ReviewView> views = reviewService.listByProduct(5L);

        assertThat(views.get(0).media()).extracting(ReviewService.MediaView::id).containsExactly(11L, 12L);
        assertThat(views.get(0).media().get(1).url()).isEqualTo("/api/reviews/media/12");
        assertThat(views.get(1).media()).isEmpty();
        assertThat(views.get(2).media()).extracting(ReviewService.MediaView::id).containsExactly(31L);
        verify(reviewMediaRepository, times(1)).findByReviewIdInOrderByReviewIdAscPositionAsc(anyCollection());
        verify(reviewMediaRepository, never()).findByReviewIdOrderByPositionAsc(any());
    }

    @Test
    void listByProductWithoutReviewsSkipsMediaQuery() {
        when(reviewRepository.findByProductIdOrderByCreatedAtDesc(5L)).thenReturn(List.of());

        assertThat(reviewService.listByProduct(5L)).isEmpty();
        verifyNoInteractions(reviewMediaRepository);
    }

    @Test
    void loadMediaReturnsStoredResourceWithDetectedContentType() {
        ByteArrayResource resource = new ByteArrayResource(mp4());
        when(reviewMediaRepository.findById(12L)).thenReturn(Optional.of(media(12L, 1L, ReviewMediaType.VIDEO, 0)));
        when(reviewMediaStorage.load("key-12")).thenReturn(resource);

        ReviewService.MediaContent content = reviewService.loadMedia(12L);

        assertThat(content.resource()).isSameAs(resource);
        assertThat(content.contentType()).isEqualTo("video/mp4");
    }

    @Test
    void loadMediaThrowsForUnknownId() {
        when(reviewMediaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.loadMedia(99L))
                .isInstanceOf(ReviewMediaNotFoundException.class);
        verifyNoInteractions(reviewMediaStorage);
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
