package com.mercatto.lists.service;

import com.mercatto.lists.domain.WishListItem;
import com.mercatto.lists.repository.WishListItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WishListItemInserterTest {

    @Mock
    private WishListItemRepository wishListItemRepository;

    @InjectMocks
    private WishListItemInserter wishListItemInserter;

    @Test
    void insertIfAbsentSavesItemAndReturnsTrue() {
        boolean inserted = wishListItemInserter.insertIfAbsent(1L, 5L);

        assertThat(inserted).isTrue();
        ArgumentCaptor<WishListItem> captor = ArgumentCaptor.forClass(WishListItem.class);
        verify(wishListItemRepository).save(captor.capture());
        assertThat(captor.getValue().getWishListId()).isEqualTo(1L);
        assertThat(captor.getValue().getProductId()).isEqualTo(5L);
    }

    @Test
    void insertIfAbsentReturnsFalseWhenConstraintViolationLosesRace() {
        when(wishListItemRepository.save(any(WishListItem.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        boolean inserted = wishListItemInserter.insertIfAbsent(1L, 5L);

        assertThat(inserted).isFalse();
    }
}
