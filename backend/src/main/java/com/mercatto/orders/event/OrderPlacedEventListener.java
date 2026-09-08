package com.mercatto.orders.event;

import com.mercatto.catalog.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
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

    private static final int MAX_ATTEMPTS = 3;

    private final ProductService productService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        event.items().forEach(this::decreaseStockWithRetry);
    }

    private void decreaseStockWithRetry(OrderPlacedEvent.Item item) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                productService.decreaseStock(item.productId(), item.quantity());
                return;
            } catch (OptimisticLockingFailureException ex) {
                // A concurrent PUT bumped Product's @Version between commit and here;
                // decreaseStock re-reads the current row on each call, so retrying
                // resolves the conflict instead of silently dropping the decrement.
                if (attempt == MAX_ATTEMPTS) {
                    log.error("Failed to decrease stock for product {} after {} attempts due to concurrent updates (order already placed)",
                            item.productId(), MAX_ATTEMPTS, ex);
                    return;
                }
                log.warn("Optimistic lock conflict decreasing stock for product {} (attempt {}/{}), retrying",
                        item.productId(), attempt, MAX_ATTEMPTS);
            } catch (Exception ex) {
                // Isolate one item's failure so it can't abort the stock decrement
                // for the order's other items, and can't escape this AFTER_COMMIT
                // callback to surface as a 500 on an already-committed order.
                log.error("Failed to decrease stock for product {} (order already placed)", item.productId(), ex);
                return;
            }
        }
    }
}
