package com.mercatto.users.service;

import com.mercatto.users.domain.User;
import com.mercatto.users.domain.UserRole;

import java.util.Optional;

/**
 * Public API of the Users module. Other modules must depend on this
 * interface only — never on the repository or the {@link User} entity's
 * persistence internals directly.
 */
public interface UserService {

    User register(String name, String email, String rawPassword, UserRole role);

    Optional<User> authenticate(String email, String rawPassword);

    Optional<User> findById(Long id);

    Optional<User> findByEmail(String email);

    boolean isSeller(Long userId);

    /**
     * Updates the profile of the given user. A {@code null} argument leaves the corresponding
     * field unchanged, so {@code updateProfile(id, null, null)} is a no-op.
     *
     * @return the updated user
     * @throws UserNotFoundException if no user exists with {@code userId}
     * @throws EmailAlreadyExistsException if {@code email} is already used by another user
     */
    User updateProfile(Long userId, String name, String email);

    /**
     * Changes the password of the given user after checking {@code currentPassword} against the
     * stored hash. The new password is stored hashed, never in clear text.
     *
     * @throws UserNotFoundException if no user exists with {@code userId}
     * @throws InvalidCurrentPasswordException if {@code currentPassword} is {@code null} or does
     *         not match the stored password (mapped to 400, never 401)
     * @throws IllegalArgumentException if {@code newPassword} is equal to {@code currentPassword}
     *         or is longer than 72 bytes in UTF-8 (BCrypt's limit)
     */
    void changePassword(Long userId, String currentPassword, String newPassword);
}
