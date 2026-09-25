package com.mercatto.reviews.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Metadata of a photo or video attached to a {@link Review}. The bytes themselves live outside
 * the database behind {@code ReviewMediaStorage}; this row only keeps the opaque storage key
 * plus what is needed to serve it back (content type, size) in the author's chosen order.
 */
@Entity
@Table(name = "review_media", schema = "reviews",
        indexes = @Index(name = "idx_review_media_review_id", columnList = "review_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Reference by id only (no @ManyToOne), same convention as the cross-module ids on Review:
    // keeps the mapping flat and the review aggregate free of lazy collections.
    @Column(name = "review_id", nullable = false)
    private Long reviewId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReviewMediaType type;

    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    // Server-generated UUID, never the client's original filename.
    @Column(name = "storage_key", nullable = false, unique = true, length = 64)
    private String storageKey;

    @Column(nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
