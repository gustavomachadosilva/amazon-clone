package com.mercatto.orders.event;

import com.mercatto.orders.domain.Order;

import java.util.List;

/**
 * Published after an order is paid. Other modules (Catalog, Sellers) react
 * to this instead of being called synchronously, so each module keeps its
 * own transaction boundary and no transaction crosses module lines.
 */
public record OrderPlacedEvent(Long orderId, Long buyerId, List<Item> items) {

    public record Item(Long productId, int quantity) {}

    public static OrderPlacedEvent from(Order order) {
        List<Item> items = order.getItems().stream()
                .map(i -> new Item(i.getProductId(), i.getQuantity()))
                .toList();
        return new OrderPlacedEvent(order.getId(), order.getBuyerId(), items);
    }
}
