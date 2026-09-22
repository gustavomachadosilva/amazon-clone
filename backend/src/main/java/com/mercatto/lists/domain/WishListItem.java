package com.mercatto.lists.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A single product entry in a {@link WishList}. References its parent list by id only
 * (plain column, not a JPA relationship) even though both entities live in this module's
 * own schema — same flat-entity style used everywhere else in the codebase.
 */
@Entity
@Table(name = "wish_list_items", schema = "lists",
        uniqueConstraints = @UniqueConstraint(name = "uk_wish_list_items_list_product",
                columnNames = {"wish_list_id", "product_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WishListItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wish_list_id", nullable = false)
    private Long wishListId;

    // Reference by id only, never a JPA relationship: the Catalog module
    // owns the Product aggregate and this module must not join across schemas.
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    @PrePersist
    void onCreate() {
        this.addedAt = Instant.now();
    }
}
