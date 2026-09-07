package com.mercatto.orders.event;

import com.mercatto.catalog.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reacts to its own module's OrderPlacedEvent by decreasing stock through
 * Catalog's public ProductService — the same allowed direction Orders
 * already depends in (see OrderServiceImpl). Runs after the placing
 * transaction commits, so a stock-update failure here never rolls back the
 * order itself; each module owns its own transaction boundary.
 */
@Component
@Slf4j
@RequiredArgsConstructor
class OrderPlacedEventListener {

    private final ProductService productService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        event.items().forEach(item -> {
            try {
                productService.decreaseStock(item.productId(), item.quantity());
            } catch (Exception ex) {
                // Isolate one item's failure (e.g. a concurrent PUT bumping Product's
                // @Version between commit and here) so it can't abort the stock
                // decrement for the order's other items, and can't escape this
                // AFTER_COMMIT callback to surface as a 500 on an already-committed order.
                log.error("Failed to decrease stock for product {} (order already placed)", item.productId(), ex);
            }
        });
    }
}
