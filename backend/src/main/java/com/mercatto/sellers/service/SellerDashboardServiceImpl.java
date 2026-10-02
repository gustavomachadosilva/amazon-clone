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
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        List<OrderService.OrderView> orders = orderService.findBySellerId(sellerId);
        Map<Long, ProductService.ProductView> products = productsById(orders, sellerId);
        return orders.stream()
                .map(order -> toSellerOrderView(order, sellerId, products))
                .toList();
    }

    // No @Transactional here: Orders owns the transaction (and the row lock) for the transition.
    @Override
    public SellerOrderView advanceFulfillment(Long sellerId, Long orderId, FulfillmentStatus next) {
        OrderService.OrderView order = orderService.advanceFulfillment(orderId, sellerId, next);
        return toSellerOrderView(order, sellerId, productsById(List.of(order), sellerId));
    }

    // One catalog lookup for every product the seller sold across these orders, so the dashboard
    // can show names and pictures instead of bare product ids. Deleted products are simply absent.
    private Map<Long, ProductService.ProductView> productsById(List<OrderService.OrderView> orders, Long sellerId) {
        Set<Long> productIds = orders.stream()
                .flatMap(order -> order.items().stream())
                .filter(item -> sellerId.equals(item.sellerId()))
                .map(OrderService.OrderItemView::productId)
                .collect(Collectors.toSet());
        return productService.findViewsByIds(productIds).stream()
                .collect(Collectors.toMap(ProductService.ProductView::id, Function.identity()));
    }

    private SellerOrderView toSellerOrderView(OrderService.OrderView order, Long sellerId,
                                              Map<Long, ProductService.ProductView> products) {
        List<SellerOrderItemView> items = order.items().stream()
                .filter(item -> sellerId.equals(item.sellerId()))
                .map(item -> toSellerOrderItemView(item, products.get(item.productId())))
                .toList();
        BigDecimal subtotal = items.stream()
                .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SellerOrderView(order.id(), order.buyerId(), order.status(), order.fulfillmentStatus(),
                order.createdAt(), items, subtotal, order.shippingAddress(), order.shippingMethod());
    }

    private SellerOrderItemView toSellerOrderItemView(OrderService.OrderItemView item, ProductService.ProductView product) {
        return new SellerOrderItemView(item.productId(),
                product == null ? null : product.name(),
                product == null ? null : product.imageUrl(),
                item.quantity(), item.unitPrice());
    }

    @Override
    public SellerMetricsView getMetrics(Long sellerId) {
        // Revenue only needs subtotals, so skip the catalog lookup getReceivedOrders does for display.
        List<SellerOrderView> orders = orderService.findBySellerId(sellerId).stream()
                .map(order -> toSellerOrderView(order, sellerId, Map.of()))
                .toList();

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
