package com.mercatto.orders.repository;

import com.mercatto.orders.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
    @Query("update Order o set o.status = com.mercatto.orders.domain.OrderStatus.PROCESSING "
            + "where o.id = :id and o.status in (com.mercatto.orders.domain.OrderStatus.PENDING, com.mercatto.orders.domain.OrderStatus.FAILED)")
    int claimForCharging(@Param("id") Long id);

    @Query("select distinct o from Order o left join fetch o.items where o.id = :id")
    Optional<Order> findByIdWithItems(@Param("id") Long id);

    @Query("select distinct o from Order o left join fetch o.items where o.buyerId = :buyerId")
    List<Order> findByBuyerIdWithItems(@Param("buyerId") Long buyerId);

    @Query("select distinct o.id from Order o join o.items oi where oi.sellerId = :sellerId")
    List<Long> findOrderIdsByItemsSellerId(@Param("sellerId") Long sellerId);

    @Query("select distinct o from Order o left join fetch o.items where o.id in :orderIds")
    List<Order> findByIdInWithItems(@Param("orderIds") List<Long> orderIds);
}
