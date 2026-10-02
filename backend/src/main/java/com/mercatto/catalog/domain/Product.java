package com.mercatto.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "products", schema = "catalog")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity;

    @Column(nullable = false)
    private String category;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    // Nullable: not every product has a known brand, model, list price or warranty term.
    // Editable per-product by the owning seller (see ProductServiceImpl#create/update).
    @Column(length = 255)
    private String brand;

    @Column(name = "warranty_months")
    private Integer warrantyMonths;

    @Column(name = "model_number", length = 255)
    private String modelNumber;

    // Reference "was" price used to compute a strikethrough discount. Only meaningful when
    // greater than price — enforced in ProductServiceImpl, not at the column level, so an
    // in-flight edit that clears/adjusts price doesn't get rejected by a DB constraint.
    @Column(name = "list_price")
    private BigDecimal listPrice;

    // Reference by id only, never a JPA relationship: the Users module owns
    // the Seller aggregate and this module must not join across schemas.
    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    // Denormalized copy of the Reviews module's aggregate, kept so search can filter and sort
    // by rating in SQL. Written only by ProductRepository#updateRating (reacting to
    // ReviewCreatedEvent, plus the boot-time ProductRatingBackfill) — insertable/updatable=false
    // so an ordinary entity save (seller PUT, decreaseStock) never overwrites it with a stale
    // value loaded earlier. New rows get the column default.
    @Column(name = "average_rating", nullable = false, insertable = false, updatable = false)
    @ColumnDefault("0")
    @Builder.Default
    @Setter(AccessLevel.NONE)
    private Double averageRating = 0.0;

    @Column(name = "review_count", nullable = false, insertable = false, updatable = false)
    @ColumnDefault("0")
    @Builder.Default
    @Setter(AccessLevel.NONE)
    private Long reviewCount = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Optimistic locking: a concurrent stock decrement (checkout) and a
    // seller edit (PUT) both read-then-write this row; without a version
    // check the later save silently overwrites the earlier one instead of
    // failing, which can revert a stock decrement invisibly.
    @Version
    private Long version;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
