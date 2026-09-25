package com.mercatto.orders.api;

import com.mercatto.orders.domain.Order;
import com.mercatto.orders.domain.PaymentMethod;
import com.mercatto.orders.domain.ShippingAddress;
import com.mercatto.orders.domain.ShippingMethod;
import com.mercatto.orders.service.OrderService;
import com.mercatto.users.domain.UserRole;
import com.mercatto.users.service.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import org.springframework.web.bind.annotation.RequestHeader;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/checkout")
    public ResponseEntity<OrderResponse> checkout(
            @Valid @RequestBody CheckoutRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Principal principal) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) principal;
        authenticatedUser.requireRole(UserRole.BUYER);
        Order order = orderService.checkout(
                authenticatedUser.userId(),
                request.items(),
                idempotencyKey,
                request.address(),
                request.shippingMethod(),
                request.paymentMethod());
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getById(@PathVariable Long id, Principal principal) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) principal;
        return orderService.findById(id)
                .map(order -> {
                    authenticatedUser.requireOwner(order.getBuyerId());
                    return ResponseEntity.ok(OrderResponse.from(order));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<OrderResponse> byBuyer(Principal principal) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) principal;
        return orderService.findByBuyer(authenticatedUser.userId()).stream()
                .map(OrderResponse::from)
                .toList();
    }

    public record CheckoutRequest(
            @NotEmpty @Valid List<OrderService.CheckoutItem> items,
            @NotNull @Valid ShippingAddress address,
            @NotNull ShippingMethod shippingMethod,
            @NotNull PaymentMethod paymentMethod) {}
}
