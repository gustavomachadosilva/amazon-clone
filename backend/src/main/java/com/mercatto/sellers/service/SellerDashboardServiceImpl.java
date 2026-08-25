package com.mercatto.sellers.service;

import com.mercatto.catalog.domain.Product;
import com.mercatto.catalog.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class SellerDashboardServiceImpl implements SellerDashboardService {

    private final ProductService productService;

    @Override
    public Page<Product> getInventory(Long sellerId, Pageable pageable) {
        return productService.findBySeller(sellerId, pageable);
    }
}
