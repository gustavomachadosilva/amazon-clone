package com.mercatto.users.service;

import com.mercatto.users.domain.User;
import com.mercatto.users.domain.UserRole;
import com.mercatto.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Captor
    private ArgumentCaptor<User> userCaptor;

    @Test
    void registerNeverPersistsThePasswordInClearText() {
        UserServiceImpl userService = new UserServiceImpl(userRepository, passwordEncoder);
        String rawPassword = "super-secret-123";
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.register("Jane Doe", "jane@example.com", rawPassword, UserRole.BUYER);

        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getPasswordHash()).isNotEqualTo(rawPassword);
        assertThat(passwordEncoder.matches(rawPassword, saved.getPasswordHash())).isTrue();
    }

    @Test
    void registerWithExistingEmail_throwsEmailAlreadyExistsException() {
        UserServiceImpl userService = new UserServiceImpl(userRepository, passwordEncoder);
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register("Jane Doe", "jane@example.com", "super-secret-123", UserRole.BUYER))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void authenticateReturnsUserWhenPasswordMatches() {
        UserServiceImpl userService = new UserServiceImpl(userRepository, passwordEncoder);
        String rawPassword = "super-secret-123";
        User user = User.builder()
                .id(1L)
                .name("Jane Doe")
                .email("jane@example.com")
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(UserRole.BUYER)
                .build();
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));

        Optional<User> result = userService.authenticate("jane@example.com", rawPassword);

        assertThat(result).contains(user);
    }

    @Test
    void authenticateReturnsEmptyWhenPasswordDoesNotMatch() {
        UserServiceImpl userService = new UserServiceImpl(userRepository, passwordEncoder);
        User user = User.builder()
                .id(1L)
                .name("Jane Doe")
                .email("jane@example.com")
                .passwordHash(passwordEncoder.encode("super-secret-123"))
                .role(UserRole.BUYER)
                .build();
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));

        Optional<User> result = userService.authenticate("jane@example.com", "wrong-password");

        assertThat(result).isEmpty();
    }

    @Test
    void authenticateReturnsEmptyWhenEmailNotFound() {
        UserServiceImpl userService = new UserServiceImpl(userRepository, passwordEncoder);
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        Optional<User> result = userService.authenticate("missing@example.com", "any-password");

        assertThat(result).isEmpty();
    }

    @Test
    void authenticateReturnsEmptyWhenPasswordIsNull() {
        UserServiceImpl userService = new UserServiceImpl(userRepository, passwordEncoder);

        Optional<User> result = userService.authenticate("jane@example.com", null);

        assertThat(result).isEmpty();
    }

    @Test
    void isSellerReturnsTrueWhenUserRoleIsSeller() {
        UserServiceImpl userService = new UserServiceImpl(userRepository, passwordEncoder);
        User sellerUser = User.builder()
                .id(1L)
                .name("Jane Doe")
                .email("jane@example.com")
                .role(UserRole.SELLER)
                .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(sellerUser));

        assertThat(userService.isSeller(1L)).isTrue();
    }

    @Test
    void isSellerReturnsFalseWhenUserRoleIsBuyer() {
        UserServiceImpl userService = new UserServiceImpl(userRepository, passwordEncoder);
        User buyerUser = User.builder()
                .id(1L)
                .name("Jane Doe")
                .email("jane@example.com")
                .role(UserRole.BUYER)
                .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(buyerUser));

        assertThat(userService.isSeller(1L)).isFalse();
    }

    @Test
    void isSellerReturnsFalseWhenUserDoesNotExist() {
        UserServiceImpl userService = new UserServiceImpl(userRepository, passwordEncoder);
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(userService.isSeller(99L)).isFalse();
    }

    @Test
    void findByIdDelegatesToRepository() {
        UserServiceImpl userService = new UserServiceImpl(userRepository, passwordEncoder);
        User user = User.builder()
                .id(1L)
                .name("Jane Doe")
                .email("jane@example.com")
                .role(UserRole.BUYER)
                .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThat(userService.findById(1L)).contains(user);
    }

    @Test
    void findByIdReturnsEmptyWhenNotFound() {
        UserServiceImpl userService = new UserServiceImpl(userRepository, passwordEncoder);
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThat(userService.findById(1L)).isEmpty();
    }

    @Test
    void findByEmailDelegatesToRepository() {
        UserServiceImpl userService = new UserServiceImpl(userRepository, passwordEncoder);
        User user = User.builder()
                .id(1L)
                .name("Jane Doe")
                .email("jane@example.com")
                .role(UserRole.BUYER)
                .build();
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));

        assertThat(userService.findByEmail("jane@example.com")).contains(user);
    }
}
