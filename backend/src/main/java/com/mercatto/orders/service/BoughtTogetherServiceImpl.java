package com.mercatto.orders.service;

import com.mercatto.catalog.service.ProductNotFoundException;
import com.mercatto.catalog.service.ProductService;
import com.mercatto.catalog.service.ProductService.ProductView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Composes Orders' co-purchase ranking with Catalog's product details, both read through their
 * public service interfaces — no transaction spans the two modules (see
 * {@link BoughtTogetherService} for why this lives in Orders).
 */
@Service
@RequiredArgsConstructor
class BoughtTogetherServiceImpl implements BoughtTogetherService {

    // Co-purchased products can be deleted or out of stock by now; asking for a few more than the
    // limit keeps the bundle full after those are dropped.
    static final int CANDIDATE_MULTIPLIER = 3;

    private final OrderService orderService;
    private final ProductService productService;

    @Override
    public BoughtTogether find(Long productId, int limit) {
        if (productService.findById(productId).isEmpty()) {
            throw new ProductNotFoundException("Product not found: " + productId);
        }

        List<OrderService.CoPurchase> coPurchases = orderService.coPurchasedWith(productId, limit * CANDIDATE_MULTIPLIER);
        if (!coPurchases.isEmpty()) {
            Map<Long, ProductView> products = productService.findViewsByIds(
                            coPurchases.stream().map(OrderService.CoPurchase::productId).toList()).stream()
                    .collect(Collectors.toMap(ProductView::id, Function.identity()));
            List<Item> items = coPurchases.stream()
                    .filter(coPurchase -> isAvailable(products.get(coPurchase.productId())))
                    .limit(limit)
                    .map(coPurchase -> new Item(products.get(coPurchase.productId()), coPurchase.buyers(), null))
                    .toList();
            if (!items.isEmpty()) {
                return new BoughtTogether(BoughtTogetherSource.CO_PURCHASE, items);
            }
        }

        List<Item> similar = productService.findRelated(productId, limit).stream()
                .map(related -> new Item(related.product(), null, related.primaryReason()))
                .toList();
        return new BoughtTogether(BoughtTogetherSource.SIMILAR, similar);
    }

    private static boolean isAvailable(ProductView product) {
        return product != null && product.stockQuantity() != null && product.stockQuantity() > 0;
    }
}
