package com.mercatto.cart.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Public API of the Cart module.
 */
public interface CartService {

    record CartItemView(Long productId, String productName, BigDecimal unitPrice, int quantity,
                         BigDecimal lineTotal, boolean savedForLater) {}

    record CartView(Long userId, List<CartItemView> items, List<CartItemView> savedForLater,
                     int itemCount, BigDecimal total) {}

    CartView getCart(Long userId);

    CartView addItem(Long userId, Long productId, int quantity);

    Optional<CartView> updateQuantity(Long userId, Long productId, int quantity);

    Optional<CartView> removeItem(Long userId, Long productId);

    Optional<CartView> saveForLater(Long userId, Long productId);

    Optional<CartView> moveToCart(Long userId, Long productId);

    CartView clear(Long userId);
}
