package com.mercatto.reviews.event;

/**
 * Published when a review is saved. Catalog reacts to it (after commit) by refreshing the
 * product's denormalized rating, so Reviews never calls into Catalog — Catalog already depends
 * on Reviews, and the reverse call would create a module cycle.
 */
public record ReviewCreatedEvent(Long reviewId, Long productId, int stars) {}
