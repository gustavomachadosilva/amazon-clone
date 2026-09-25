package com.mercatto.reviews.service;

import org.springframework.core.io.InputStreamSource;
import org.springframework.core.io.Resource;

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

    /**
     * A photo or video attached to a review. {@code type} is {@code IMAGE} or {@code VIDEO};
     * {@code url} is the public endpoint that streams the file.
     */
    record MediaView(Long id, String type, String url) {}

    record ReviewView(Long id, Long productId, Long authorId, String authorName, int stars, String title,
                       String text, long helpfulCount, Instant createdAt, List<MediaView> media) {}

    /**
     * One uploaded file. {@code content} must be re-readable (e.g. a {@code MultipartFile}): the
     * service reads its header once to detect the real type and then again to store it.
     */
    record MediaUpload(InputStreamSource content, long sizeBytes) {}

    /**
     * Stored bytes of a review photo/video plus the content type detected at upload time.
     */
    record MediaContent(Resource resource, String contentType) {}

    default ReviewView createReview(Long productId, Long authorId, int stars, String title, String text) {
        return createReview(productId, authorId, stars, title, text, List.of());
    }

    ReviewView createReview(Long productId, Long authorId, int stars, String title, String text,
                            List<MediaUpload> media);

    List<ReviewView> listByProduct(Long productId);

    ReviewView markHelpful(Long reviewId);

    MediaContent loadMedia(Long mediaId);

    RatingAggregate getAggregate(Long productId);

    Map<Long, RatingAggregate> getAggregates(Collection<Long> productIds);
}
