package com.mercatto.orders.api;

import com.mercatto.orders.domain.Order;
import com.mercatto.orders.service.OrderService;
import com.mercatto.users.domain.UserRole;
import com.mercatto.users.service.AuthenticatedUser;
import com.mercatto.users.service.ForbiddenRoleException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/checkout")
    public ResponseEntity<Order> checkout(@Valid @RequestBody CheckoutRequest request, Principal principal) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) principal;
        if (authenticatedUser.role() != UserRole.BUYER) {
            throw new ForbiddenRoleException("Apenas compradores podem finalizar pedidos");
        }
        Order order = orderService.checkout(authenticatedUser.userId(), request.items());
        return ResponseEntity.ok(order);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getById(@PathVariable Long id) {
        return orderService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<Order> byBuyer(Principal principal) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) principal;
        return orderService.findByBuyer(authenticatedUser.userId());
    }

    public record CheckoutRequest(@NotEmpty @Valid List<OrderService.CheckoutItem> items) {}
}
