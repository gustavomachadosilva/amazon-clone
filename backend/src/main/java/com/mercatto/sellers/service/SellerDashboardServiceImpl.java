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
import java.util.List;

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
        return orderService.findBySellerId(sellerId).stream()
                .map(order -> toSellerOrderView(order, sellerId))
                .toList();
    }

    private SellerOrderView toSellerOrderView(Order order, Long sellerId) {
        List<SellerOrderItemView> items = order.getItems().stream()
                .filter(item -> sellerId.equals(item.getSellerId()))
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
