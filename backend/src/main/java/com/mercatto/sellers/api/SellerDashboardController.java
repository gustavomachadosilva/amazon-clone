package com.mercatto.sellers.api;

import com.mercatto.catalog.domain.Product;
import com.mercatto.sellers.service.SellerDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sellers/{sellerId}")
@RequiredArgsConstructor
public class SellerDashboardController {

    private final SellerDashboardService sellerDashboardService;

    @GetMapping("/products")
    public Page<Product> inventory(@PathVariable Long sellerId, Pageable pageable) {
        return sellerDashboardService.getInventory(sellerId, pageable);
    }
}
