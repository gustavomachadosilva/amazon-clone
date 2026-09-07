package com.mercatto.orders.service;

import com.mercatto.catalog.domain.Product;
import com.mercatto.catalog.service.ProductService;
import com.mercatto.orders.domain.OrderItem;
import com.mercatto.orders.repository.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Backfills {@code OrderItem.sellerId} for rows written before that column
 * was denormalized from {@code Product.sellerId} at checkout time. Runs on
 * every boot and is a no-op once nothing is missing it; reads Catalog only
 * through its public {@link ProductService} (the same allowed direction
 * {@link OrderServiceImpl} already depends in), so this stays a
 * single-module (Orders) write. An item whose product was since deleted
 * from the catalog can't be backfilled and is left as-is.
 */
@Component
@Slf4j
@RequiredArgsConstructor
class OrderItemSellerIdBackfill {

    private final OrderItemRepository orderItemRepository;
    private final ProductService productService;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfillMissingSellerIds() {
        List<OrderItem> items = orderItemRepository.findBySellerIdIsNull();
        if (items.isEmpty()) {
            return;
        }
        int updated = 0;
        for (OrderItem item : items) {
            Long sellerId = productService.findById(item.getProductId())
                    .map(Product::getSellerId)
                    .orElse(null);
            if (sellerId != null) {
                item.setSellerId(sellerId);
                updated++;
            }
        }
        orderItemRepository.saveAll(items);
        log.info("Backfilled seller_id on {}/{} order items missing it", updated, items.size());
    }
}
