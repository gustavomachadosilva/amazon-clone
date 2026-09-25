package com.mercatto.sellers.service;

import com.mercatto.catalog.service.ProductService;
import com.mercatto.orders.service.FulfillmentStatus;
import com.mercatto.orders.service.OrderService;
import com.mercatto.orders.service.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
class SellerDashboardServiceImpl implements SellerDashboardService {

    private final ProductService productService;
    private final OrderService orderService;

    @Override
    public Page<ProductService.ProductSummary> getInventory(Long sellerId, Pageable pageable) {
        return productService.findBySeller(sellerId, pageable);
    }

    @Override
    public List<SellerOrderView> getReceivedOrders(Long sellerId) {
        return orderService.findBySellerId(sellerId).stream()
                .map(order -> toSellerOrderView(order, sellerId))
                .toList();
    }

    // No @Transactional here: Orders owns the transaction (and the row lock) for the transition.
    @Override
    public SellerOrderView advanceFulfillment(Long sellerId, Long orderId, FulfillmentStatus next) {
        return toSellerOrderView(orderService.advanceFulfillment(orderId, sellerId, next), sellerId);
    }

    private SellerOrderView toSellerOrderView(OrderService.OrderView order, Long sellerId) {
        List<SellerOrderItemView> items = order.items().stream()
                .filter(item -> sellerId.equals(item.sellerId()))
                .map(this::toSellerOrderItemView)
                .toList();
        BigDecimal subtotal = items.stream()
                .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SellerOrderView(order.id(), order.buyerId(), order.status(), order.fulfillmentStatus(),
                order.createdAt(), items, subtotal);
    }

    private SellerOrderItemView toSellerOrderItemView(OrderService.OrderItemView item) {
        return new SellerOrderItemView(item.productId(), item.quantity(), item.unitPrice());
    }

    @Override
    public SellerMetricsView getMetrics(Long sellerId) {
        List<SellerOrderView> orders = getReceivedOrders(sellerId);

        BigDecimal totalRevenue = orders.stream()
                .filter(o -> o.status() == OrderStatus.PAID)
                .map(SellerOrderView::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // A threshold of 10 for low stock
        List<ProductService.ProductSummary> lowStockProducts = productService.findBySeller(sellerId, Pageable.unpaged())
                .stream()
                .filter(p -> p.stockQuantity() < 10)
                .toList();

        return new SellerMetricsView(totalRevenue, lowStockProducts);
    }
}
