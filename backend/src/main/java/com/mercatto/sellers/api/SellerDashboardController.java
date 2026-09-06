package com.mercatto.sellers.api;

import com.mercatto.catalog.domain.Product;
import com.mercatto.orders.domain.Order;
import com.mercatto.sellers.service.SellerDashboardService;
import com.mercatto.users.domain.UserRole;
import com.mercatto.users.service.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/sellers/{sellerId}")
@RequiredArgsConstructor
public class SellerDashboardController {

    private final SellerDashboardService sellerDashboardService;

    @GetMapping("/products")
    public Page<Product> inventory(@PathVariable Long sellerId, Pageable pageable, Principal principal) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) principal;
        authenticatedUser.requireRole(UserRole.SELLER);
        authenticatedUser.requireOwner(sellerId);
        return sellerDashboardService.getInventory(sellerId, pageable);
    }

    @GetMapping("/orders")
    public List<Order> receivedOrders(@PathVariable Long sellerId, Principal principal) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) principal;
        authenticatedUser.requireRole(UserRole.SELLER);
        authenticatedUser.requireOwner(sellerId);
        return sellerDashboardService.getReceivedOrders(sellerId);
    }
}
