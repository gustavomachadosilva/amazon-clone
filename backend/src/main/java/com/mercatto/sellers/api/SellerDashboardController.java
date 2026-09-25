package com.mercatto.sellers.api;

import com.mercatto.catalog.service.ProductService;
import com.mercatto.orders.service.FulfillmentStatus;
import com.mercatto.sellers.service.SellerDashboardService;
import com.mercatto.users.domain.UserRole;
import com.mercatto.users.service.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    public Page<ProductService.ProductSummary> inventory(@PathVariable Long sellerId, Pageable pageable, Principal principal) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) principal;
        authenticatedUser.requireRole(UserRole.SELLER);
        authenticatedUser.requireOwner(sellerId);
        return sellerDashboardService.getInventory(sellerId, pageable);
    }

    @GetMapping("/orders")
    public List<SellerDashboardService.SellerOrderView> receivedOrders(@PathVariable Long sellerId, Principal principal) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) principal;
        authenticatedUser.requireRole(UserRole.SELLER);
        authenticatedUser.requireOwner(sellerId);
        return sellerDashboardService.getReceivedOrders(sellerId);
    }

    @GetMapping("/metrics")
    public SellerDashboardService.SellerMetricsView metrics(@PathVariable Long sellerId, Principal principal) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) principal;
        authenticatedUser.requireRole(UserRole.SELLER);
        authenticatedUser.requireOwner(sellerId);
        return sellerDashboardService.getMetrics(sellerId);
    }

    @PostMapping("/orders/{orderId}/fulfillment")
    public SellerDashboardService.SellerOrderView advanceFulfillment(
            @PathVariable Long sellerId,
            @PathVariable Long orderId,
            @Valid @RequestBody AdvanceFulfillmentRequest request,
            Principal principal) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) principal;
        authenticatedUser.requireRole(UserRole.SELLER);
        authenticatedUser.requireOwner(sellerId);
        return sellerDashboardService.advanceFulfillment(sellerId, orderId, request.status());
    }

    public record AdvanceFulfillmentRequest(@NotNull FulfillmentStatus status) {}
}
