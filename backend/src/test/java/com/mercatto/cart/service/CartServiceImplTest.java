package com.mercatto.cart.service;

import com.mercatto.cart.domain.CartItem;
import com.mercatto.cart.repository.CartItemRepository;
import com.mercatto.catalog.domain.Product;
import com.mercatto.catalog.service.ProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductService productService;

    @InjectMocks
    private CartServiceImpl cartService;

    private static Product product(long id, String name, String price) {
        return Product.builder().id(id).name(name).price(new BigDecimal(price)).stockQuantity(100).build();
    }

    @Test
    void addItemMergesQuantityIntoExistingActiveLine() {
        CartItem existing = CartItem.builder().id(1L).userId(10L).productId(1L).quantity(2).savedForLater(false).build();
        when(productService.findById(1L)).thenReturn(Optional.of(product(1L, "Widget", "9.99")));
        when(cartItemRepository.findByUserIdAndProductId(10L, 1L)).thenReturn(Optional.of(existing));
        when(cartItemRepository.findByUserId(10L)).thenReturn(List.of(existing));

        cartService.addItem(10L, 1L, 3);

        ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualTo(5);
        assertThat(captor.getValue().isSavedForLater()).isFalse();
    }

    @Test
    void addItemThrowsWhenProductDoesNotExistInCatalog() {
        when(productService.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addItem(10L, 99L, 1))
                .isInstanceOf(IllegalArgumentException.class);

        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void updateQuantityReturnsEmptyWhenItemNotInCart() {
        when(cartItemRepository.findByUserIdAndProductId(10L, 1L)).thenReturn(Optional.empty());

        assertThat(cartService.updateQuantity(10L, 1L, 5)).isEmpty();
    }

    @Test
    void removeItemReturnsEmptyWhenItemNotInCart() {
        when(cartItemRepository.findByUserIdAndProductId(10L, 1L)).thenReturn(Optional.empty());

        assertThat(cartService.removeItem(10L, 1L)).isEmpty();
    }

    @Test
    void saveForLaterReturnsEmptyWhenItemNotInCart() {
        when(cartItemRepository.findByUserIdAndProductId(10L, 1L)).thenReturn(Optional.empty());

        assertThat(cartService.saveForLater(10L, 1L)).isEmpty();
    }

    @Test
    void moveToCartReturnsEmptyWhenItemNotInCart() {
        when(cartItemRepository.findByUserIdAndProductId(10L, 1L)).thenReturn(Optional.empty());

        assertThat(cartService.moveToCart(10L, 1L)).isEmpty();
    }

    @Test
    void saveForLaterFollowedByMoveToCartRestoresFlag() {
        CartItem item = CartItem.builder().id(1L).userId(10L).productId(1L).quantity(2).savedForLater(false).build();
        when(cartItemRepository.findByUserIdAndProductId(10L, 1L)).thenReturn(Optional.of(item));
        when(cartItemRepository.findByUserId(10L)).thenReturn(List.of(item));
        when(productService.findById(1L)).thenReturn(Optional.of(product(1L, "Widget", "9.99")));

        cartService.saveForLater(10L, 1L);
        assertThat(item.isSavedForLater()).isTrue();

        cartService.moveToCart(10L, 1L);
        assertThat(item.isSavedForLater()).isFalse();

        verify(cartItemRepository, times(2)).save(item);
    }

    @Test
    void clearDeletesAllItemsForUser() {
        when(cartItemRepository.findByUserId(10L)).thenReturn(List.of());

        cartService.clear(10L);

        verify(cartItemRepository).deleteByUserId(10L);
    }

    @Test
    void getCartDiscardsOrphanProductAndSumsOnlyActiveItems() {
        CartItem active = CartItem.builder().id(1L).userId(10L).productId(1L).quantity(2).savedForLater(false).build();
        CartItem savedForLater = CartItem.builder().id(2L).userId(10L).productId(2L).quantity(1).savedForLater(true).build();
        CartItem orphan = CartItem.builder().id(3L).userId(10L).productId(3L).quantity(4).savedForLater(false).build();

        when(cartItemRepository.findByUserId(10L)).thenReturn(List.of(active, savedForLater, orphan));
        when(productService.findById(1L)).thenReturn(Optional.of(product(1L, "Widget", "10.00")));
        when(productService.findById(2L)).thenReturn(Optional.of(product(2L, "Gadget", "5.00")));
        when(productService.findById(3L)).thenReturn(Optional.empty());

        CartService.CartView view = cartService.getCart(10L);

        assertThat(view.items()).hasSize(1);
        assertThat(view.items().get(0).productId()).isEqualTo(1L);
        assertThat(view.savedForLater()).hasSize(1);
        assertThat(view.savedForLater().get(0).productId()).isEqualTo(2L);
        assertThat(view.itemCount()).isEqualTo(2);
        assertThat(view.total()).isEqualByComparingTo("20.00");
    }
}
