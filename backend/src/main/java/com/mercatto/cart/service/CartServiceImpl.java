package com.mercatto.cart.service;

import com.mercatto.cart.domain.CartItem;
import com.mercatto.cart.repository.CartItemRepository;
import com.mercatto.catalog.domain.Product;
import com.mercatto.catalog.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Cart calls Catalog synchronously through {@link ProductService} (its
 * public API) to resolve product name/price at read time and to validate
 * that a product exists when it is added to a cart. This is a read-only,
 * never-mutating call, so it is safe inside this module's own transactions —
 * no cross-module mutation happens here, and therefore no
 * {@code ApplicationEvent} is needed to keep this module's transactions from
 * spanning Catalog's tables.
 */
@Service
@RequiredArgsConstructor
class CartServiceImpl implements CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductService productService;

    @Override
    public CartView getCart(Long userId) {
        return toView(userId, cartItemRepository.findByUserId(userId));
    }

    @Override
    @Transactional
    public CartView addItem(Long userId, Long productId, int quantity) {
        productService.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        CartItem item = cartItemRepository.findByUserIdAndProductId(userId, productId)
                .orElseGet(() -> CartItem.builder()
                        .userId(userId)
                        .productId(productId)
                        .quantity(0)
                        .savedForLater(false)
                        .build());

        item.setQuantity(item.getQuantity() + quantity);
        item.setSavedForLater(false);
        cartItemRepository.save(item);

        return getCart(userId);
    }

    @Override
    @Transactional
    public Optional<CartView> updateQuantity(Long userId, Long productId, int quantity) {
        return cartItemRepository.findByUserIdAndProductId(userId, productId)
                .map(item -> {
                    item.setQuantity(quantity);
                    cartItemRepository.save(item);
                    return getCart(userId);
                });
    }

    @Override
    @Transactional
    public Optional<CartView> removeItem(Long userId, Long productId) {
        return cartItemRepository.findByUserIdAndProductId(userId, productId)
                .map(item -> {
                    cartItemRepository.delete(item);
                    return getCart(userId);
                });
    }

    @Override
    @Transactional
    public Optional<CartView> saveForLater(Long userId, Long productId) {
        return cartItemRepository.findByUserIdAndProductId(userId, productId)
                .map(item -> {
                    item.setSavedForLater(true);
                    cartItemRepository.save(item);
                    return getCart(userId);
                });
    }

    @Override
    @Transactional
    public Optional<CartView> moveToCart(Long userId, Long productId) {
        return cartItemRepository.findByUserIdAndProductId(userId, productId)
                .map(item -> {
                    item.setSavedForLater(false);
                    cartItemRepository.save(item);
                    return getCart(userId);
                });
    }

    @Override
    @Transactional
    public CartView clear(Long userId) {
        cartItemRepository.deleteByUserId(userId);
        return getCart(userId);
    }

    private CartView toView(Long userId, List<CartItem> cartItems) {
        List<CartItemView> items = new ArrayList<>();
        List<CartItemView> saved = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int itemCount = 0;

        for (CartItem cartItem : cartItems) {
            // The product may have been removed from the catalog after being
            // added to a cart; such an orphaned line is silently dropped from
            // the view rather than surfaced as an error, since the cart
            // itself is still valid without it.
            Optional<Product> product = productService.findById(cartItem.getProductId());
            if (product.isEmpty()) {
                continue;
            }

            BigDecimal lineTotal = product.get().getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            CartItemView view = new CartItemView(
                    cartItem.getProductId(),
                    product.get().getName(),
                    product.get().getPrice(),
                    cartItem.getQuantity(),
                    lineTotal,
                    cartItem.isSavedForLater());

            if (cartItem.isSavedForLater()) {
                saved.add(view);
            } else {
                items.add(view);
                total = total.add(lineTotal);
                itemCount += cartItem.getQuantity();
            }
        }

        return new CartView(userId, items, saved, itemCount, total);
    }
}
