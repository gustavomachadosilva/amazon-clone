package com.mercatto.lists.repository;

import com.mercatto.lists.domain.WishListItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WishListItemRepository extends JpaRepository<WishListItem, Long> {

    List<WishListItem> findByWishListId(Long wishListId);

    List<WishListItem> findByWishListIdIn(List<Long> wishListIds);

    Optional<WishListItem> findByWishListIdAndProductId(Long wishListId, Long productId);

    void deleteByWishListId(Long wishListId);
}
