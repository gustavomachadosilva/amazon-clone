package com.mercatto.lists.service;

import java.time.Instant;
import java.util.List;

/**
 * Public API of the Lists module.
 */
public interface WishListService {

    record WishListView(Long id, Long buyerId, String name, List<Long> productIds, Instant createdAt) {}

    record AddItemResult(WishListView list, boolean alreadyPresent) {}

    WishListView createList(Long buyerId, String name);

    List<WishListView> listByBuyer(Long buyerId);

    AddItemResult addItem(Long listId, Long buyerId, Long productId);

    WishListView removeItem(Long listId, Long buyerId, Long productId);

    void deleteList(Long listId, Long buyerId);
}
