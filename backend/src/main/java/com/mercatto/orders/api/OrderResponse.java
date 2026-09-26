package com.mercatto.orders.api;

import com.mercatto.orders.domain.DeliveryEstimator;
import com.mercatto.orders.domain.Order;
import com.mercatto.orders.domain.OrderItem;
import com.mercatto.orders.domain.PaymentMethod;
import com.mercatto.orders.domain.ShippingAddress;
import com.mercatto.orders.domain.ShippingMethod;
import com.mercatto.orders.service.FulfillmentStatus;
import com.mercatto.orders.service.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * HTTP representation of an {@link Order}, so the order endpoints never serialize the JPA
 * entity directly (#142 / #172). Keeps every field the frontend already reads; the
 * internal {@code idempotencyKey} is deliberately not exposed.
 * {@code address}, {@code shippingMethod} and {@code paymentMethod} are null for orders
 * placed before those fields existed.
 */
public record OrderResponse(
        Long id,
        Long buyerId,
        OrderStatus status,
        FulfillmentStatus fulfillmentStatus,
        BigDecimal totalAmount,
        AddressResponse address,
        ShippingMethod shippingMethod,
        PaymentMethod paymentMethod,
        List<ItemResponse> items,
        Instant createdAt,
        Instant shippedAt,
        Instant outForDeliveryAt,
        Instant deliveredAt,
        LocalDate estimatedDeliveryDate) {

    public record ItemResponse(Long id, Long productId, Long sellerId, Integer quantity, BigDecimal unitPrice) {

        static ItemResponse from(OrderItem item) {
            return new ItemResponse(
                    item.getId(), item.getProductId(), item.getSellerId(), item.getQuantity(), item.getUnitPrice());
        }
    }

    public record AddressResponse(String fullName, String street, String city, String state, String zip) {

        static AddressResponse from(ShippingAddress address) {
            if (address == null) {
                return null;
            }
            return new AddressResponse(
                    address.getFullName(), address.getStreet(), address.getCity(), address.getState(), address.getZip());
        }
    }

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getBuyerId(),
                order.getStatus(),
                order.getFulfillmentStatus(),
                order.getTotalAmount(),
                AddressResponse.from(order.getAddress()),
                order.getShippingMethod(),
                order.getPaymentMethod(),
                order.getItems().stream().map(ItemResponse::from).toList(),
                order.getCreatedAt(),
                order.getShippedAt(),
                order.getOutForDeliveryAt(),
                order.getDeliveredAt(),
                // Counted from the payment, not the order: a FAILED order paid days later through a
                // retry would otherwise be promised a date already in the past. Orders paid before
                // paid_at existed have none, and were paid at checkout.
                DeliveryEstimator.estimate(
                        order.getPaidAt() != null ? order.getPaidAt() : order.getCreatedAt(),
                        order.getShippingMethod()));
    }
}
