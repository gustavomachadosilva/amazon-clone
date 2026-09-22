package com.mercatto.lists.service;

import com.mercatto.lists.domain.WishListItem;
import com.mercatto.lists.repository.WishListItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inserts a wish-list item in its own transaction, so a unique-constraint race (two
 * concurrent adds of the same product to the same list) aborts only this insert instead
 * of the caller's transaction — same REQUIRES_NEW pattern as {@code OrderReservationService}.
 */
@Service
@RequiredArgsConstructor
class WishListItemInserter {

    private final WishListItemRepository wishListItemRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean insertIfAbsent(Long listId, Long productId) {
        try {
            wishListItemRepository.save(WishListItem.builder()
                    .wishListId(listId)
                    .productId(productId)
                    .build());
            return true;
        } catch (DataIntegrityViolationException raceLost) {
            return false;
        }
    }
}
