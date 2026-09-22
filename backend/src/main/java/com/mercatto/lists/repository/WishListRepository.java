package com.mercatto.lists.repository;

import com.mercatto.lists.domain.WishList;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WishListRepository extends JpaRepository<WishList, Long> {

    List<WishList> findByBuyerIdOrderByCreatedAtDesc(Long buyerId);
}
