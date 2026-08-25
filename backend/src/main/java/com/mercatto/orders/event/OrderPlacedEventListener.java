package com.mercatto.orders.event;

import com.mercatto.catalog.service.ProductService;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
class OrderPlacedEventListener {

    private final ProductService productService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        event.items().forEach(item -> productService.decreaseStock(item.productId(), item.quantity()));
    }
}
