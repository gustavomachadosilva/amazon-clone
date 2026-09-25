package com.mercatto.orders.repository;

import com.mercatto.orders.domain.Order;
import com.mercatto.orders.domain.PaymentMethod;
import com.mercatto.orders.service.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByBuyerId(Long buyerId);

    @Query("select distinct o from Order o left join fetch o.items where o.buyerId = :buyerId and o.idempotencyKey = :idempotencyKey")
    Optional<Order> findByBuyerIdAndIdempotencyKey(@Param("buyerId") Long buyerId, @Param("idempotencyKey") String idempotencyKey);

    // Atomic claim gate: only a PENDING/FAILED order can be claimed for charging, and only
    // once — the WHERE clause makes this a compare-and-swap, so two concurrent retries of the
    // same order (same idempotency key) can never both proceed to charge the payment gateway.
    @Modifying
    @Query("update Order o set o.status = com.mercatto.orders.service.OrderStatus.PROCESSING "
            + "where o.id = :id and o.status in (com.mercatto.orders.service.OrderStatus.PENDING, com.mercatto.orders.service.OrderStatus.FAILED)")
    int claimForCharging(@Param("id") Long id);

    // Claim gate for a buyer-initiated payment retry (#174): same compare-and-swap as
    // claimForCharging, but only from FAILED (a PENDING order is still inside its checkout) and
    // it records the payment method chosen for the retry in the same atomic update.
    @Modifying
    @Query("update Order o set o.status = com.mercatto.orders.service.OrderStatus.PROCESSING, "
            + "o.paymentMethod = :paymentMethod "
            + "where o.id = :id and o.status = com.mercatto.orders.service.OrderStatus.FAILED")
    int claimFailedForRetry(@Param("id") Long id, @Param("paymentMethod") PaymentMethod paymentMethod);

    // Writes only the status column: the PAID/FAILED update after a charge holds an Order read
    // before the gateway call, and merging that whole stale copy would revert anything committed
    // meanwhile (e.g. a shipping address the buyer changed while the payment was in flight).
    @Modifying(clearAutomatically = true)
    @Query("update Order o set o.status = :status where o.id = :id")
    int updateStatus(@Param("id") Long id, @Param("status") OrderStatus status);

    // Same column-only write as updateStatus, for the PAID outcome: also records when it was paid.
    @Modifying(clearAutomatically = true)
    @Query("update Order o set o.status = com.mercatto.orders.service.OrderStatus.PAID, o.paidAt = :paidAt "
            + "where o.id = :id")
    int markPaid(@Param("id") Long id, @Param("paidAt") Instant paidAt);

    @Query("select distinct o from Order o left join fetch o.items where o.id = :id")
    Optional<Order> findByIdWithItems(@Param("id") Long id);

    // Row lock (SELECT ... FOR UPDATE) so two concurrent fulfillment advances of the same
    // order serialize instead of both passing the transition check. No join fetch: Postgres
    // rejects FOR UPDATE on the nullable side of an outer join; items load lazily instead.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    @Query("select distinct o from Order o left join fetch o.items where o.buyerId = :buyerId")
    List<Order> findByBuyerIdWithItems(@Param("buyerId") Long buyerId);

    @Query("select distinct o.id from Order o join o.items oi where oi.sellerId = :sellerId")
    List<Long> findOrderIdsByItemsSellerId(@Param("sellerId") Long sellerId);

    @Query("select distinct o from Order o left join fetch o.items where o.id in :orderIds")
    List<Order> findByIdInWithItems(@Param("orderIds") List<Long> orderIds);
}
