package com.mercatto.lists.service;

import com.mercatto.catalog.service.ProductNotFoundException;
import com.mercatto.catalog.service.ProductService;
import com.mercatto.lists.domain.WishList;
import com.mercatto.lists.domain.WishListItem;
import com.mercatto.lists.repository.WishListItemRepository;
import com.mercatto.lists.repository.WishListRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Lists calls Catalog synchronously through {@link ProductService} (its public API) to
 * validate that a product exists when it is added to a wish list. This is a read-only,
 * never-mutating call, so it is safe inside this module's own transactions — same pattern
 * as {@code CartServiceImpl}.
 */
@Service
@RequiredArgsConstructor
class WishListServiceImpl implements WishListService {

    private final WishListRepository wishListRepository;
    private final WishListItemRepository wishListItemRepository;
    private final ProductService productService;
    private final WishListItemInserter wishListItemInserter;

    @Override
    @Transactional
    public WishListView createList(Long buyerId, String name) {
        WishList list = WishList.builder()
                .buyerId(buyerId)
                .name(name)
                .build();
        WishList saved = wishListRepository.save(list);
        return toView(saved, List.of());
    }

    @Override
    public List<WishListView> listByBuyer(Long buyerId) {
        List<WishList> lists = wishListRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId);
        List<Long> listIds = lists.stream().map(WishList::getId).toList();
        Map<Long, List<Long>> productIdsByListId = wishListItemRepository.findByWishListIdIn(listIds).stream()
                .collect(Collectors.groupingBy(WishListItem::getWishListId,
                        Collectors.mapping(WishListItem::getProductId, Collectors.toList())));

        return lists.stream()
                .map(list -> toView(list, productIdsByListId.getOrDefault(list.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional
    public AddItemResult addItem(Long listId, Long buyerId, Long productId) {
        WishList list = findOwnedList(listId, buyerId);

        productService.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productId));

        Optional<WishListItem> existing = wishListItemRepository.findByWishListIdAndProductId(listId, productId);
        if (existing.isPresent()) {
            return new AddItemResult(toView(list, productIdsOf(listId)), true);
        }

        // The check above is only a fast path: two concurrent adds can both pass it, so
        // the actual idempotency guarantee comes from the unique constraint on
        // (wish_list_id, product_id), enforced by this insert.
        boolean inserted = wishListItemInserter.insertIfAbsent(listId, productId);

        return new AddItemResult(toView(list, productIdsOf(listId)), !inserted);
    }

    @Override
    @Transactional
    public WishListView removeItem(Long listId, Long buyerId, Long productId) {
        WishList list = findOwnedList(listId, buyerId);

        wishListItemRepository.findByWishListIdAndProductId(listId, productId)
                .ifPresent(wishListItemRepository::delete);

        return toView(list, productIdsOf(listId));
    }

    @Override
    @Transactional
    public void deleteList(Long listId, Long buyerId) {
        WishList list = findOwnedList(listId, buyerId);

        wishListItemRepository.deleteByWishListId(listId);
        wishListRepository.delete(list);
    }

    private WishList findOwnedList(Long listId, Long buyerId) {
        WishList list = wishListRepository.findById(listId)
                .orElseThrow(() -> new WishListNotFoundException("Wish list not found: " + listId));
        // A list owned by a different buyer is reported as not found rather than
        // forbidden, so a request never reveals whether another buyer's list exists.
        if (!list.getBuyerId().equals(buyerId)) {
            throw new WishListNotFoundException("Wish list not found: " + listId);
        }
        return list;
    }

    private List<Long> productIdsOf(Long listId) {
        return wishListItemRepository.findByWishListId(listId).stream()
                .map(WishListItem::getProductId)
                .toList();
    }

    private WishListView toView(WishList list, List<Long> productIds) {
        return new WishListView(list.getId(), list.getBuyerId(), list.getName(), productIds, list.getCreatedAt());
    }
}
