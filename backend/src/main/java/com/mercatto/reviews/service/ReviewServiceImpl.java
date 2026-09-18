package com.mercatto.reviews.service;

import com.mercatto.reviews.domain.Review;
import com.mercatto.reviews.repository.ReviewRepository;
import com.mercatto.users.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
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
 */
@Service
@RequiredArgsConstructor
class ReviewServiceImpl implements ReviewService {

    private static final String FALLBACK_AUTHOR_NAME = "Usuário";

    private final ReviewRepository reviewRepository;
    private final UserService userService;

    @Override
    @Transactional
    public ReviewView createReview(Long productId, Long authorId, int stars, String title, String text) {
        if (stars < 1 || stars > 5) {
            throw new IllegalArgumentException("stars must be between 1 and 5");
        }

        Review review = Review.builder()
                .productId(productId)
                .authorId(authorId)
                .stars(stars)
                .title(title)
                .text(text)
                .helpfulCount(0)
                .build();
        Review saved = reviewRepository.save(review);
        return toView(saved);
    }

    @Override
    public List<ReviewView> listByProduct(Long productId) {
        return reviewRepository.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .map(this::toView)
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
        return toView(review);
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

    private ReviewView toView(Review review) {
        String authorName = userService.findById(review.getAuthorId())
                .map(user -> user.getName())
                .filter(name -> name != null && !name.isBlank())
                .orElse(FALLBACK_AUTHOR_NAME);

        return new ReviewView(
                review.getId(),
                review.getProductId(),
                review.getAuthorId(),
                authorName,
                review.getStars(),
                review.getTitle(),
                review.getText(),
                review.getHelpfulCount(),
                review.getCreatedAt());
    }
}
