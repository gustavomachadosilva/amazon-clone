package com.mercatto.sellers.service;

import com.mercatto.catalog.domain.Product;
import com.mercatto.catalog.service.ProductService;
import com.mercatto.orders.domain.Order;
import com.mercatto.orders.domain.OrderItem;
import com.mercatto.orders.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
class SellerDashboardServiceImpl implements SellerDashboardService {

    private final ProductService productService;
    private final OrderService orderService;

    @Override
    public Page<Product> getInventory(Long sellerId, Pageable pageable) {
        return productService.findBySeller(sellerId, pageable);
    }

    @Override
    public List<SellerOrderView> getReceivedOrders(Long sellerId) {
        List<Long> productIds = productService.findProductIdsBySeller(sellerId);
        if (productIds.isEmpty()) {
            return List.of();
        }
        Set<Long> ownProductIds = new HashSet<>(productIds);
        return orderService.findByProductIds(productIds).stream()
                .map(order -> toSellerOrderView(order, ownProductIds))
                .toList();
    }

    private SellerOrderView toSellerOrderView(Order order, Set<Long> ownProductIds) {
        List<SellerOrderItemView> items = order.getItems().stream()
                .filter(item -> ownProductIds.contains(item.getProductId()))
                .map(this::toSellerOrderItemView)
                .toList();
        BigDecimal subtotal = items.stream()
                .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SellerOrderView(order.getId(), order.getBuyerId(), order.getStatus(), order.getCreatedAt(),
                items, subtotal);
    }

    private SellerOrderItemView toSellerOrderItemView(OrderItem item) {
        return new SellerOrderItemView(item.getProductId(), item.getQuantity(), item.getUnitPrice());
    }
}
