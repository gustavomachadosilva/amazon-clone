package com.mercatto.users.service;

import com.mercatto.users.domain.User;
import com.mercatto.users.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSeederTest {

    @Mock
    private UserService userService;

    private User userFor(String email, UserRole role) {
        return User.builder()
                .id((long) email.hashCode())
                .name("Seed User")
                .email(email)
                .passwordHash("hashed")
                .role(role)
                .build();
    }

    @Test
    void seedOnEmptyDatabaseRegistersEveryUserOnce() {
        UserSeeder seeder = new UserSeeder(userService);
        when(userService.register(anyString(), anyString(), anyString(), any(UserRole.class)))
                .thenAnswer(invocation -> userFor(invocation.getArgument(1), invocation.getArgument(3)));

        List<User> result = seeder.seed();

        assertThat(result).hasSize(9);
        verify(userService, times(9)).register(anyString(), anyString(), anyString(), any(UserRole.class));
        verify(userService, never()).findByEmail(anyString());
    }

    @Test
    void seedWhenAllUsersAlreadyExistFallsBackToFindByEmailWithoutPropagatingException() {
        UserSeeder seeder = new UserSeeder(userService);
        when(userService.register(anyString(), anyString(), anyString(), any(UserRole.class)))
                .thenThrow(new EmailAlreadyExistsException("já existe"));
        when(userService.findByEmail(anyString()))
                .thenAnswer(invocation -> Optional.of(userFor(invocation.getArgument(0), UserRole.BUYER)));

        List<User> result = seeder.seed();

        assertThat(result).hasSize(9);
        verify(userService, times(9)).findByEmail(anyString());
    }

    @Test
    void seedResultIncludesTheDefaultSellerWithSellerRole() {
        UserSeeder seeder = new UserSeeder(userService);
        when(userService.register(anyString(), anyString(), anyString(), any(UserRole.class)))
                .thenAnswer(invocation -> userFor(invocation.getArgument(1), invocation.getArgument(3)));

        List<User> result = seeder.seed();

        assertThat(result)
                .filteredOn(u -> u.getEmail().equals(UserSeeder.DEFAULT_SELLER_EMAIL))
                .hasSize(1)
                .allMatch(u -> u.getRole() == UserRole.SELLER);
    }

    @Test
    void seedRegistersDefaultSellerWithExpectedCredentials() {
        UserSeeder seeder = new UserSeeder(userService);
        when(userService.register(anyString(), anyString(), anyString(), any(UserRole.class)))
                .thenAnswer(invocation -> userFor(invocation.getArgument(1), invocation.getArgument(3)));

        seeder.seed();

        verify(userService).register(anyString(), eq(UserSeeder.DEFAULT_SELLER_EMAIL), anyString(), eq(UserRole.SELLER));
    }
}
