package com.mercatto.users.service;

import com.mercatto.users.domain.User;
import com.mercatto.users.domain.UserRole;
import com.mercatto.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public User register(String name, String email, String rawPassword, UserRole role) {
        User user = User.builder()
                .name(name)
                .email(email)
                // TODO: replace with a real password encoder once the Auth module is plugged in.
                .passwordHash(rawPassword)
                .role(role)
                .build();
        return userRepository.save(user);
    }

    @Override
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    @Override
    public boolean isSeller(Long userId) {
        return userRepository.findById(userId)
                .map(user -> user.getRole() == UserRole.SELLER)
                .orElse(false);
    }
}
