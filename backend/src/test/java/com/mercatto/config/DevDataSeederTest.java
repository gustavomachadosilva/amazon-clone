package com.mercatto.config;

import com.mercatto.catalog.service.DummyJsonSeeder;
import com.mercatto.users.domain.User;
import com.mercatto.users.domain.UserRole;
import com.mercatto.users.service.UserSeeder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DevDataSeederTest {

    @Mock
    private UserSeeder userSeeder;

    @Mock
    private DummyJsonSeeder dummyJsonSeeder;

    private User userWith(Long id, String email, UserRole role) {
        return User.builder().id(id).name("Seed User").email(email).passwordHash("hashed").role(role).build();
    }

    @Test
    void seedDevDataSeedsUsersBeforeProductsAndUsesTheDefaultSellerId() {
        DevDataSeeder devDataSeeder = new DevDataSeeder(userSeeder, dummyJsonSeeder);
        User seller = userWith(42L, UserSeeder.DEFAULT_SELLER_EMAIL, UserRole.SELLER);
        User buyer = userWith(1L, "buyer@mercatto.dev", UserRole.BUYER);
        when(userSeeder.seed()).thenReturn(List.of(buyer, seller));

        devDataSeeder.seedDevData();

        InOrder inOrder = inOrder(userSeeder, dummyJsonSeeder);
        inOrder.verify(userSeeder).seed();
        inOrder.verify(dummyJsonSeeder).seedProducts(42L);
    }

    @Test
    void seedDevDataThrowsIllegalStateExceptionWhenDefaultSellerIsMissing() {
        DevDataSeeder devDataSeeder = new DevDataSeeder(userSeeder, dummyJsonSeeder);
        User buyer = userWith(1L, "buyer@mercatto.dev", UserRole.BUYER);
        when(userSeeder.seed()).thenReturn(List.of(buyer));

        assertThatThrownBy(devDataSeeder::seedDevData)
                .isInstanceOf(IllegalStateException.class);

        verify(dummyJsonSeeder, never()).seedProducts(any());
    }
}
