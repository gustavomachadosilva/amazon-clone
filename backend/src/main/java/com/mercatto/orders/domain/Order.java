package com.mercatto.orders.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import com.mercatto.orders.service.FulfillmentStatus;
import com.mercatto.orders.service.OrderStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "orders",
        schema = "orders",
        uniqueConstraints = @UniqueConstraint(name = "uk_orders_buyer_idempotency_key", columnNames = {"buyer_id", "idempotency_key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Reference by id only; the Users module owns the Buyer aggregate.
    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    // Snapshot of the buyer's shipping address at checkout time (same principle as
    // OrderItem.sellerId — denormalized so it survives later changes elsewhere).
    // Nullable: pre-existing rows created before this field was added won't have it set.
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "fullName", column = @Column(name = "address_full_name")),
            @AttributeOverride(name = "street", column = @Column(name = "address_street")),
            @AttributeOverride(name = "city", column = @Column(name = "address_city")),
            @AttributeOverride(name = "state", column = @Column(name = "address_state")),
            @AttributeOverride(name = "zip", column = @Column(name = "address_zip"))
    })
    private ShippingAddress address;

    @Enumerated(EnumType.STRING)
    @Column(name = "shipping_method")
    private ShippingMethod shippingMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Shipping lifecycle, separate from the payment status above. Only changed through
    // advanceFulfillmentTo (no setters) so the status and its timestamp stay in sync.
    // The column default lets ddl-auto add this NOT NULL column to existing rows, which
    // then read as NOT_SHIPPED.
    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_status", nullable = false, length = 32)
    @ColumnDefault("'NOT_SHIPPED'")
    @Builder.Default
    @Setter(AccessLevel.NONE)
    private FulfillmentStatus fulfillmentStatus = FulfillmentStatus.NOT_SHIPPED;

    @Column(name = "shipped_at")
    @Setter(AccessLevel.NONE)
    private Instant shippedAt;

    @Column(name = "out_for_delivery_at")
    @Setter(AccessLevel.NONE)
    private Instant outForDeliveryAt;

    @Column(name = "delivered_at")
    @Setter(AccessLevel.NONE)
    private Instant deliveredAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        if (this.status == null) {
            this.status = OrderStatus.PENDING;
        }
        if (this.fulfillmentStatus == null) {
            this.fulfillmentStatus = FulfillmentStatus.NOT_SHIPPED;
        }
    }

    /**
     * Moves the order to {@code next} and records when it happened. Transition rules
     * (immediate next state only, PAID orders only) are enforced by the caller,
     * {@code OrderService.advanceFulfillment}.
     */
    public void advanceFulfillmentTo(FulfillmentStatus next, Instant at) {
        switch (next) {
            case SHIPPED -> this.shippedAt = at;
            case OUT_FOR_DELIVERY -> this.outForDeliveryAt = at;
            case DELIVERED -> this.deliveredAt = at;
            case NOT_SHIPPED -> throw new IllegalArgumentException("Cannot advance fulfillment to NOT_SHIPPED");
        }
        this.fulfillmentStatus = next;
    }

    public void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
    }
}
