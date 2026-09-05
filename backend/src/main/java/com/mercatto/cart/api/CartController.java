package com.mercatto.cart.api;

import com.mercatto.cart.service.CartService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping("/{userId}")
    public CartService.CartView getCart(@PathVariable Long userId) {
        return cartService.getCart(userId);
    }

    @PostMapping("/{userId}/items")
    public CartService.CartView addItem(@PathVariable Long userId, @Valid @RequestBody AddItemRequest request) {
        return cartService.addItem(userId, request.productId(), request.quantity());
    }

    @PutMapping("/{userId}/items/{productId}")
    public ResponseEntity<CartService.CartView> updateQuantity(@PathVariable Long userId,
                                                                 @PathVariable Long productId,
                                                                 @Valid @RequestBody UpdateQuantityRequest request) {
        return cartService.updateQuantity(userId, productId, request.quantity())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{userId}/items/{productId}")
    public ResponseEntity<CartService.CartView> removeItem(@PathVariable Long userId, @PathVariable Long productId) {
        return cartService.removeItem(userId, productId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{userId}/items/{productId}/save-for-later")
    public ResponseEntity<CartService.CartView> saveForLater(@PathVariable Long userId, @PathVariable Long productId) {
        return cartService.saveForLater(userId, productId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{userId}/items/{productId}/move-to-cart")
    public ResponseEntity<CartService.CartView> moveToCart(@PathVariable Long userId, @PathVariable Long productId) {
        return cartService.moveToCart(userId, productId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{userId}")
    public CartService.CartView clear(@PathVariable Long userId) {
        return cartService.clear(userId);
    }

    public record AddItemRequest(@NotNull @Positive Long productId, @Positive int quantity) {}

    public record UpdateQuantityRequest(@Positive int quantity) {}
}
