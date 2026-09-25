package com.mercatto.reviews.repository;

import com.mercatto.reviews.domain.ReviewMedia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReviewMediaRepository extends JpaRepository<ReviewMedia, Long> {

    List<ReviewMedia> findByReviewIdOrderByPositionAsc(Long reviewId);

    // Batch variant for listing a product's reviews: one query for every review's media
    // instead of one per review (avoids N+1).
    List<ReviewMedia> findByReviewIdInOrderByReviewIdAscPositionAsc(Collection<Long> reviewIds);
}
