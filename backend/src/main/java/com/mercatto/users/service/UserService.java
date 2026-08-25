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

    Optional<User> findById(Long id);

    boolean isSeller(Long userId);
}
