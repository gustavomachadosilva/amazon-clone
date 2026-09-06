package com.mercatto.sellers.service;

import com.mercatto.catalog.domain.Product;
import com.mercatto.catalog.service.ProductService;
import com.mercatto.orders.domain.Order;
import com.mercatto.orders.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

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
    public List<Order> getReceivedOrders(Long sellerId) {
        List<Long> productIds = productService.findProductIdsBySeller(sellerId);
        if (productIds.isEmpty()) {
            return List.of();
        }
        return orderService.findByProductIds(productIds);
    }
}
