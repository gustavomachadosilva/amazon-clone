package com.mercatto.cart.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A single line in a buyer's cart. There is no separate "Cart" aggregate: the
 * cart has no identity of its own beyond {@link #userId}, so this is the only
 * entity in the module's schema. Price and product name are never stored
 * here — they are resolved at read time from Catalog's public
 * {@code ProductService}, so this table never goes stale relative to the
 * catalog.
 */
@Entity
@Table(name = "cart_items", schema = "cart",
        uniqueConstraints = @UniqueConstraint(name = "uk_cart_items_user_product", columnNames = {"user_id", "product_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Reference by id only, never a JPA relationship: the Users module owns
    // the Buyer aggregate and this module must not join across schemas.
    @Column(name = "user_id", nullable = false)
    private Long userId;

    // Reference by id only, never a JPA relationship: the Catalog module
    // owns the Product aggregate and this module must not join across schemas.
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "saved_for_later", nullable = false)
    private boolean savedForLater;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.addedAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
