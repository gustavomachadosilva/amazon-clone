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
}
