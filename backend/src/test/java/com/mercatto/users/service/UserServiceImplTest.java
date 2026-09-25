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
import static org.mockito.ArgumentMatchers.anyString;
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

    private User existingUser() {
        return User.builder()
                .id(1L)
                .name("Jane Doe")
                .email("jane@example.com")
                .passwordHash("$2a$10$hash")
                .role(UserRole.BUYER)
                .build();
    }

    @Test
    void updateProfileUpdatesNameAndEmail() {
        UserServiceImpl userService = new UserServiceImpl(userRepository, passwordEncoder);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser()));
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.updateProfile(1L, "New Name", "new@example.com");

        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getName()).isEqualTo("New Name");
        assertThat(saved.getEmail()).isEqualTo("new@example.com");
        assertThat(result).isSameAs(saved);
    }

    @Test
    void updateProfileWithOnlyNameKeepsEmailAndSkipsEmailCheck() {
        UserServiceImpl userService = new UserServiceImpl(userRepository, passwordEncoder);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser()));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.updateProfile(1L, "New Name", null);

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getEmail()).isEqualTo("jane@example.com");
        verify(userRepository, never()).existsByEmail(anyString());
    }

    @Test
    void updateProfileWithOnlyEmailKeepsName() {
        UserServiceImpl userService = new UserServiceImpl(userRepository, passwordEncoder);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser()));
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.updateProfile(1L, null, "new@example.com");

        assertThat(result.getName()).isEqualTo("Jane Doe");
        assertThat(result.getEmail()).isEqualTo("new@example.com");
    }

    @Test
    void updateProfileWithSameEmailDoesNotCheckForDuplicates() {
        UserServiceImpl userService = new UserServiceImpl(userRepository, passwordEncoder);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser()));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.updateProfile(1L, null, "jane@example.com");

        assertThat(result.getEmail()).isEqualTo("jane@example.com");
        verify(userRepository, never()).existsByEmail(anyString());
    }

    @Test
    void updateProfileWithEmailOfAnotherUser_throwsEmailAlreadyExistsException() {
        UserServiceImpl userService = new UserServiceImpl(userRepository, passwordEncoder);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser()));
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.updateProfile(1L, "New Name", "taken@example.com"))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessage("E-mail já cadastrado: taken@example.com");

        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfileWithAllNullIsNoOp() {
        UserServiceImpl userService = new UserServiceImpl(userRepository, passwordEncoder);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser()));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.updateProfile(1L, null, null);

        assertThat(result.getName()).isEqualTo("Jane Doe");
        assertThat(result.getEmail()).isEqualTo("jane@example.com");
        verify(userRepository, never()).existsByEmail(anyString());
    }

    @Test
    void updateProfileWhenUserMissing_throwsUserNotFoundException() {
        UserServiceImpl userService = new UserServiceImpl(userRepository, passwordEncoder);
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateProfile(99L, "New Name", "new@example.com"))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("Usuário não encontrado: 99");

        verify(userRepository, never()).save(any());
    }

    private static final String CURRENT_PASSWORD = "current-secret-123";
    private static final String NEW_PASSWORD = "brand-new-secret-456";

    private User userWithPassword(String rawPassword) {
        return User.builder()
                .id(1L)
                .name("Jane Doe")
                .email("jane@example.com")
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(UserRole.BUYER)
                .build();
    }

    @Test
    void changePasswordWithCorrectCurrent_storesHashOfNewPassword() {
        UserServiceImpl userService = new UserServiceImpl(userRepository, passwordEncoder);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithPassword(CURRENT_PASSWORD)));

        userService.changePassword(1L, CURRENT_PASSWORD, NEW_PASSWORD);

        verify(userRepository).save(userCaptor.capture());
        String savedHash = userCaptor.getValue().getPasswordHash();
        assertThat(savedHash).isNotEqualTo(NEW_PASSWORD);
        assertThat(passwordEncoder.matches(NEW_PASSWORD, savedHash)).isTrue();
        assertThat(passwordEncoder.matches(CURRENT_PASSWORD, savedHash)).isFalse();
    }

    @Test
    void changePasswordWithWrongCurrent_throwsInvalidCurrentPasswordException() {
        UserServiceImpl userService = new UserServiceImpl(userRepository, passwordEncoder);
        User user = userWithPassword(CURRENT_PASSWORD);
        String originalHash = user.getPasswordHash();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.changePassword(1L, "wrong-password", NEW_PASSWORD))
                .isInstanceOf(InvalidCurrentPasswordException.class)
                .hasMessage("Senha atual incorreta");

        assertThat(user.getPasswordHash()).isEqualTo(originalHash);
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePasswordWithNullCurrent_throwsInvalidCurrentPasswordException() {
        UserServiceImpl userService = new UserServiceImpl(userRepository, passwordEncoder);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithPassword(CURRENT_PASSWORD)));

        assertThatThrownBy(() -> userService.changePassword(1L, null, NEW_PASSWORD))
                .isInstanceOf(InvalidCurrentPasswordException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void changePasswordWithNewEqualToCurrent_throwsIllegalArgumentException() {
        UserServiceImpl userService = new UserServiceImpl(userRepository, passwordEncoder);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithPassword(CURRENT_PASSWORD)));

        assertThatThrownBy(() -> userService.changePassword(1L, CURRENT_PASSWORD, CURRENT_PASSWORD))
                .isInstanceOf(IllegalArgumentException.class)
                .isNotInstanceOf(InvalidCurrentPasswordException.class)
                .hasMessage("A nova senha deve ser diferente da senha atual");

        verify(userRepository, never()).save(any());
    }

    @Test
    void changePasswordWhenUserMissing_throwsUserNotFoundException() {
        UserServiceImpl userService = new UserServiceImpl(userRepository, passwordEncoder);
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.changePassword(99L, CURRENT_PASSWORD, NEW_PASSWORD))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("Usuário não encontrado: 99");

        verify(userRepository, never()).save(any());
    }

    @Test
    void changePasswordWithNewPasswordOver72Bytes_throwsIllegalArgumentException() {
        UserServiceImpl userService = new UserServiceImpl(userRepository, passwordEncoder);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithPassword(CURRENT_PASSWORD)));
        // 40 characters (passes the @Size(max = 72) check) but 80 bytes in UTF-8.
        String multiByte = "é".repeat(40);

        assertThatThrownBy(() -> userService.changePassword(1L, CURRENT_PASSWORD, multiByte))
                .isInstanceOf(IllegalArgumentException.class)
                .isNotInstanceOf(InvalidCurrentPasswordException.class)
                .hasMessage("A senha deve ter no máximo 72 bytes");

        verify(userRepository, never()).save(any());
    }

    @Test
    void changePasswordWithNewPasswordOfExactly72Bytes_succeeds() {
        UserServiceImpl userService = new UserServiceImpl(userRepository, passwordEncoder);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithPassword(CURRENT_PASSWORD)));

        userService.changePassword(1L, CURRENT_PASSWORD, "é".repeat(36));

        verify(userRepository).save(any());
    }

    @Test
    void registerWithPasswordOver72Bytes_throwsIllegalArgumentException() {
        UserServiceImpl userService = new UserServiceImpl(userRepository, passwordEncoder);
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);

        assertThatThrownBy(() -> userService.register("Jane", "jane@example.com", "é".repeat(40), UserRole.BUYER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A senha deve ter no máximo 72 bytes");

        verify(userRepository, never()).save(any());
    }
}
