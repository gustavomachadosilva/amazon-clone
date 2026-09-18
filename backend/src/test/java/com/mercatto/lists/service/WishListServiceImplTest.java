package com.mercatto.lists.service;

import com.mercatto.catalog.domain.Product;
import com.mercatto.catalog.service.ProductNotFoundException;
import com.mercatto.catalog.service.ProductService;
import com.mercatto.lists.domain.WishList;
import com.mercatto.lists.domain.WishListItem;
import com.mercatto.lists.repository.WishListItemRepository;
import com.mercatto.lists.repository.WishListRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WishListServiceImplTest {

    @Mock
    private WishListRepository wishListRepository;

    @Mock
    private WishListItemRepository wishListItemRepository;

    @Mock
    private ProductService productService;

    @InjectMocks
    private WishListServiceImpl wishListService;

    private static WishList list(long id, long buyerId, String name) {
        return WishList.builder()
                .id(id)
                .buyerId(buyerId)
                .name(name)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }

    private static WishListItem item(long id, long wishListId, long productId) {
        return WishListItem.builder()
                .id(id)
                .wishListId(wishListId)
                .productId(productId)
                .addedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }

    private static Product product(long id) {
        return Product.builder().id(id).name("Product " + id).build();
    }

    @Test
    void createListSavesAndReturnsView() {
        when(wishListRepository.save(any(WishList.class))).thenAnswer(invocation -> {
            WishList saved = invocation.getArgument(0);
            saved.setId(1L);
            saved.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
            return saved;
        });

        WishListService.WishListView view = wishListService.createList(10L, "Birthday");

        assertThat(view.id()).isEqualTo(1L);
        assertThat(view.buyerId()).isEqualTo(10L);
        assertThat(view.name()).isEqualTo("Birthday");
        assertThat(view.productIds()).isEmpty();

        ArgumentCaptor<WishList> captor = ArgumentCaptor.forClass(WishList.class);
        verify(wishListRepository).save(captor.capture());
        assertThat(captor.getValue().getBuyerId()).isEqualTo(10L);
        assertThat(captor.getValue().getName()).isEqualTo("Birthday");
    }

    @Test
    void listByBuyerReturnsViewsWithProductIds() {
        WishList l1 = list(1L, 10L, "Birthday");
        when(wishListRepository.findByBuyerIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(l1));
        when(wishListItemRepository.findByWishListId(1L)).thenReturn(List.of(item(100L, 1L, 5L), item(101L, 1L, 6L)));

        List<WishListService.WishListView> views = wishListService.listByBuyer(10L);

        assertThat(views).hasSize(1);
        assertThat(views.get(0).productIds()).containsExactly(5L, 6L);
    }

    @Test
    void addItemWithUnknownProductThrowsAndDoesNotSave() {
        WishList l1 = list(1L, 10L, "Birthday");
        when(wishListRepository.findById(1L)).thenReturn(Optional.of(l1));
        when(productService.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> wishListService.addItem(1L, 10L, 99L))
                .isInstanceOf(ProductNotFoundException.class);

        verify(wishListItemRepository, never()).save(any());
    }

    @Test
    void addItemIsIdempotentWhenProductAlreadyPresent() {
        WishList l1 = list(1L, 10L, "Birthday");
        when(wishListRepository.findById(1L)).thenReturn(Optional.of(l1));
        when(productService.findById(5L)).thenReturn(Optional.of(product(5L)));
        when(wishListItemRepository.findByWishListIdAndProductId(1L, 5L))
                .thenReturn(Optional.of(item(100L, 1L, 5L)));
        when(wishListItemRepository.findByWishListId(1L)).thenReturn(List.of(item(100L, 1L, 5L)));

        WishListService.AddItemResult result = wishListService.addItem(1L, 10L, 5L);

        assertThat(result.alreadyPresent()).isTrue();
        assertThat(result.list().productIds()).containsExactly(5L);
        verify(wishListItemRepository, never()).save(any());
    }

    @Test
    void addItemSavesNewItemWhenNotYetPresent() {
        WishList l1 = list(1L, 10L, "Birthday");
        when(wishListRepository.findById(1L)).thenReturn(Optional.of(l1));
        when(productService.findById(5L)).thenReturn(Optional.of(product(5L)));
        when(wishListItemRepository.findByWishListIdAndProductId(1L, 5L)).thenReturn(Optional.empty());
        when(wishListItemRepository.findByWishListId(1L)).thenReturn(List.of(item(100L, 1L, 5L)));

        WishListService.AddItemResult result = wishListService.addItem(1L, 10L, 5L);

        assertThat(result.alreadyPresent()).isFalse();
        assertThat(result.list().productIds()).containsExactly(5L);

        ArgumentCaptor<WishListItem> captor = ArgumentCaptor.forClass(WishListItem.class);
        verify(wishListItemRepository).save(captor.capture());
        assertThat(captor.getValue().getWishListId()).isEqualTo(1L);
        assertThat(captor.getValue().getProductId()).isEqualTo(5L);
    }

    @Test
    void addItemByNonOwnerThrowsWishListNotFound() {
        WishList l1 = list(1L, 10L, "Birthday");
        when(wishListRepository.findById(1L)).thenReturn(Optional.of(l1));

        assertThatThrownBy(() -> wishListService.addItem(1L, 999L, 5L))
                .isInstanceOf(WishListNotFoundException.class);

        verify(wishListItemRepository, never()).save(any());
    }

    @Test
    void removeItemByNonOwnerThrowsWishListNotFound() {
        WishList l1 = list(1L, 10L, "Birthday");
        when(wishListRepository.findById(1L)).thenReturn(Optional.of(l1));

        assertThatThrownBy(() -> wishListService.removeItem(1L, 999L, 5L))
                .isInstanceOf(WishListNotFoundException.class);

        verify(wishListItemRepository, never()).delete(any());
    }

    @Test
    void deleteListByNonOwnerThrowsWishListNotFound() {
        WishList l1 = list(1L, 10L, "Birthday");
        when(wishListRepository.findById(1L)).thenReturn(Optional.of(l1));

        assertThatThrownBy(() -> wishListService.deleteList(1L, 999L))
                .isInstanceOf(WishListNotFoundException.class);

        verify(wishListRepository, never()).delete(any());
    }

    @Test
    void removeItemWithUnknownListIdThrows() {
        when(wishListRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> wishListService.removeItem(99L, 10L, 5L))
                .isInstanceOf(WishListNotFoundException.class);
    }

    @Test
    void deleteListWithUnknownListIdThrows() {
        when(wishListRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> wishListService.deleteList(99L, 10L))
                .isInstanceOf(WishListNotFoundException.class);
    }

    @Test
    void removeItemDeletesMatchingItemAndReturnsUpdatedView() {
        WishList l1 = list(1L, 10L, "Birthday");
        WishListItem existing = item(100L, 1L, 5L);
        when(wishListRepository.findById(1L)).thenReturn(Optional.of(l1));
        when(wishListItemRepository.findByWishListIdAndProductId(1L, 5L)).thenReturn(Optional.of(existing));
        when(wishListItemRepository.findByWishListId(1L)).thenReturn(List.of());

        WishListService.WishListView view = wishListService.removeItem(1L, 10L, 5L);

        assertThat(view.productIds()).isEmpty();
        verify(wishListItemRepository).delete(existing);
    }

    @Test
    void deleteListDeletesItemsThenList() {
        WishList l1 = list(1L, 10L, "Birthday");
        when(wishListRepository.findById(1L)).thenReturn(Optional.of(l1));

        wishListService.deleteList(1L, 10L);

        verify(wishListItemRepository).deleteByWishListId(1L);
        verify(wishListRepository).delete(l1);
    }
}
