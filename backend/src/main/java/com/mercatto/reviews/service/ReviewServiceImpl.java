package com.mercatto.reviews.service;

import com.mercatto.reviews.domain.Review;
import com.mercatto.reviews.domain.ReviewMedia;
import com.mercatto.reviews.domain.ReviewMediaType;
import com.mercatto.reviews.repository.ReviewMediaRepository;
import com.mercatto.reviews.repository.ReviewRepository;
import com.mercatto.users.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reviews calls Users synchronously through {@link UserService} (its public API) to resolve
 * the author's display name at read time, mirroring how {@code CartServiceImpl} resolves
 * product name/price at read time through Catalog's {@code ProductService}. This is a
 * read-only, never-mutating call, so it is safe inside this module's own transactions.
 *
 * <p>Deliberately does NOT validate {@code productId} against Catalog's {@code ProductService}:
 * Catalog already depends on Reviews ({@code ProductServiceImpl} calls {@code ReviewService} for
 * aggregate ratings), so a reverse Reviews -> Catalog call here would create a module cycle,
 * which {@code ArchitectureBoundaryTest.modules_should_be_free_of_cycles} correctly rejects.
 *
 * <p>Review photos/videos: every file is validated (count, magic-byte type, size) before
 * anything touches the disk; files are then written through {@link ReviewMediaStorage} before
 * the rows are saved, and deleted again if the method fails or the transaction does not commit,
 * so a rejected or rolled-back review never leaves orphan files behind.
 */
@Service
@RequiredArgsConstructor
class ReviewServiceImpl implements ReviewService {

    private static final String FALLBACK_AUTHOR_NAME = "Usuário";

    static final int MAX_FILES = 5;
    static final String ALLOWED_MSG = "Only JPEG, PNG, WebP, MP4 or WebM files are allowed";
    static final String MEDIA_URL_PREFIX = "/api/reviews/media/";

    private final ReviewRepository reviewRepository;
    private final ReviewMediaRepository reviewMediaRepository;
    private final ReviewMediaStorage reviewMediaStorage;
    private final UserService userService;

    @Override
    @Transactional
    public ReviewView createReview(Long productId, Long authorId, int stars, String title, String text,
                                   List<MediaUpload> media) {
        if (stars < 1 || stars > 5) {
            throw new IllegalArgumentException("stars must be between 1 and 5");
        }
        List<MediaUpload> uploads = media == null ? List.of() : media;
        List<ReviewMediaTypeDetector.DetectedMedia> detected = validate(uploads);

        List<String> storedKeys = new ArrayList<>();
        if (!uploads.isEmpty() && TransactionSynchronizationManager.isSynchronizationActive()) {
            // Covers failures after this method returns (e.g. the commit itself failing).
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        deleteAll(storedKeys);
                    }
                }
            });
        }

        try {
            for (MediaUpload upload : uploads) {
                try (InputStream in = upload.content().getInputStream()) {
                    storedKeys.add(reviewMediaStorage.store(in));
                } catch (IOException e) {
                    throw new UncheckedIOException("Could not store review media", e);
                }
            }

            Review saved = reviewRepository.save(Review.builder()
                    .productId(productId)
                    .authorId(authorId)
                    .stars(stars)
                    .title(title)
                    .text(text)
                    .helpfulCount(0)
                    .build());

            List<ReviewMedia> rows = new ArrayList<>();
            for (int i = 0; i < uploads.size(); i++) {
                ReviewMediaTypeDetector.DetectedMedia info = detected.get(i);
                rows.add(ReviewMedia.builder()
                        .reviewId(saved.getId())
                        .type(info.type())
                        .contentType(info.contentType())
                        .sizeBytes(uploads.get(i).sizeBytes())
                        .storageKey(storedKeys.get(i))
                        .position(i)
                        .build());
            }
            List<ReviewMedia> savedMedia = rows.isEmpty() ? List.of() : reviewMediaRepository.saveAll(rows);
            return toView(saved, savedMedia);
        } catch (RuntimeException | Error e) {
            deleteAll(storedKeys);
            throw e;
        }
    }

    @Override
    public List<ReviewView> listByProduct(Long productId) {
        List<Review> reviews = reviewRepository.findByProductIdOrderByCreatedAtDesc(productId);
        if (reviews.isEmpty()) {
            return List.of();
        }

        Map<Long, List<ReviewMedia>> mediaByReview = new LinkedHashMap<>();
        List<Long> reviewIds = reviews.stream().map(Review::getId).toList();
        for (ReviewMedia m : reviewMediaRepository.findByReviewIdInOrderByReviewIdAscPositionAsc(reviewIds)) {
            mediaByReview.computeIfAbsent(m.getReviewId(), id -> new ArrayList<>()).add(m);
        }

        return reviews.stream()
                .map(review -> toView(review, mediaByReview.getOrDefault(review.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional
    public ReviewView markHelpful(Long reviewId) {
        int updated = reviewRepository.incrementHelpfulCount(reviewId);
        if (updated == 0) {
            throw new ReviewNotFoundException("Review not found: " + reviewId);
        }
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ReviewNotFoundException("Review not found: " + reviewId));
        return toView(review, reviewMediaRepository.findByReviewIdOrderByPositionAsc(reviewId));
    }

    @Override
    public MediaContent loadMedia(Long mediaId) {
        ReviewMedia media = reviewMediaRepository.findById(mediaId)
                .orElseThrow(() -> new ReviewMediaNotFoundException("Review media not found: " + mediaId));
        return new MediaContent(reviewMediaStorage.load(media.getStorageKey()), media.getContentType());
    }

    @Override
    public RatingAggregate getAggregate(Long productId) {
        ReviewRepository.RatingAggregateProjection projection = reviewRepository.aggregateForProduct(productId);
        long count = projection != null && projection.getReviewCount() != null ? projection.getReviewCount() : 0L;
        if (count == 0) {
            return RatingAggregate.empty(productId);
        }
        double average = projection.getAverageRating() != null ? projection.getAverageRating() : 0.0;
        return new RatingAggregate(productId, average, count);
    }

    @Override
    public Map<Long, RatingAggregate> getAggregates(Collection<Long> productIds) {
        Map<Long, RatingAggregate> aggregates = new HashMap<>();
        if (productIds == null || productIds.isEmpty()) {
            return aggregates;
        }

        for (ReviewRepository.ProductRatingAggregateProjection projection : reviewRepository.aggregateForProducts(productIds)) {
            long count = projection.getReviewCount() != null ? projection.getReviewCount() : 0L;
            double average = projection.getAverageRating() != null ? projection.getAverageRating() : 0.0;
            aggregates.put(projection.getProductId(), new RatingAggregate(projection.getProductId(), average, count));
        }

        for (Long productId : productIds) {
            aggregates.putIfAbsent(productId, RatingAggregate.empty(productId));
        }

        return aggregates;
    }

    /**
     * Checks every upload before any of them is written, so a bad file in the batch rejects the
     * whole review without touching the disk. Returns the detected type of each file, in order.
     */
    private List<ReviewMediaTypeDetector.DetectedMedia> validate(List<MediaUpload> uploads) {
        if (uploads.size() > MAX_FILES) {
            throw new InvalidReviewMediaException("A review can have at most " + MAX_FILES + " photos or videos");
        }
        List<ReviewMediaTypeDetector.DetectedMedia> detected = new ArrayList<>();
        for (MediaUpload upload : uploads) {
            if (upload == null || upload.content() == null || upload.sizeBytes() <= 0) {
                throw new InvalidReviewMediaException("Empty files are not allowed");
            }
            ReviewMediaTypeDetector.DetectedMedia info = ReviewMediaTypeDetector.detect(readHeader(upload))
                    .orElseThrow(() -> new InvalidReviewMediaException(ALLOWED_MSG));
            if (upload.sizeBytes() > info.maxBytes()) {
                throw new InvalidReviewMediaException(info.type() == ReviewMediaType.IMAGE
                        ? "Images must be at most 5 MB"
                        : "Videos must be at most 50 MB");
            }
            detected.add(info);
        }
        return detected;
    }

    private static byte[] readHeader(MediaUpload upload) {
        try (InputStream in = upload.content().getInputStream()) {
            return in.readNBytes(ReviewMediaTypeDetector.HEADER_SIZE);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read uploaded review media", e);
        }
    }

    private void deleteAll(List<String> keys) {
        for (String key : keys) {
            reviewMediaStorage.delete(key);
        }
    }

    private ReviewView toView(Review review, List<ReviewMedia> media) {
        String authorName = userService.findById(review.getAuthorId())
                .map(user -> user.getName())
                .filter(name -> name != null && !name.isBlank())
                .orElse(FALLBACK_AUTHOR_NAME);

        List<MediaView> mediaViews = media.stream()
                .map(m -> new MediaView(m.getId(), m.getType().name(), MEDIA_URL_PREFIX + m.getId()))
                .toList();

        return new ReviewView(
                review.getId(),
                review.getProductId(),
                review.getAuthorId(),
                authorName,
                review.getStars(),
                review.getTitle(),
                review.getText(),
                review.getHelpfulCount(),
                review.getCreatedAt(),
                mediaViews);
    }
}
