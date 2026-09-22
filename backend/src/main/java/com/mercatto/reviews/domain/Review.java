package com.mercatto.reviews.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A single buyer review of a product. There is no separate "Rating" aggregate: the
 * average rating and review count are derived on read from this table, so a review
 * is the only entity in the module's schema.
 */
@Entity
@Table(name = "reviews", schema = "reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Reference by id only, never a JPA relationship: the Catalog module
    // owns the Product aggregate and this module must not join across schemas.
    @Column(name = "product_id", nullable = false)
    private Long productId;

    // Reference by id only, never a JPA relationship: the Users module owns
    // the Buyer aggregate and this module must not join across schemas.
    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(nullable = false)
    private Integer stars;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String text;

    @Column(name = "helpful_count", nullable = false)
    @Builder.Default
    private Integer helpfulCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        if (this.helpfulCount == null) {
            this.helpfulCount = 0;
        }
    }
}
