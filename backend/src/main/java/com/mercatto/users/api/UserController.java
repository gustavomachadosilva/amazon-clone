package com.mercatto.users.api;

import com.mercatto.users.domain.User;
import com.mercatto.users.domain.UserRole;
import com.mercatto.users.service.AuthenticatedUser;
import com.mercatto.users.service.TokenService;
import com.mercatto.users.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Instant;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final TokenService tokenService;

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.register(request.name(), request.email(), request.password(), request.role());
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(Principal principal) {
        Long userId = ((AuthenticatedUser) principal).userId();
        return userService.findById(userId)
                .map(UserResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateMe(@Valid @RequestBody UpdateProfileRequest request, Principal principal) {
        Long userId = ((AuthenticatedUser) principal).userId();
        User user = userService.updateProfile(userId, request.name(), request.email());
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changeMyPassword(@Valid @RequestBody ChangePasswordRequest request, Principal principal) {
        Long userId = ((AuthenticatedUser) principal).userId();
        userService.changePassword(userId, request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getById(@PathVariable Long id, Principal principal) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) principal;
        authenticatedUser.requireOwner(id);
        return userService.findById(id)
                .map(UserResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        return userService.authenticate(request.email(), request.password())
                .map(user -> {
                    TokenService.IssuedToken issuedToken = tokenService.issue(user.getId(), user.getRole());
                    return LoginResponse.from(user, issuedToken);
                })
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotNull UserRole role) {}

    public record LoginRequest(String email, String password) {}

    /**
     * Partial profile update: an absent ({@code null}) field is left unchanged, but a field that
     * is present must not be blank.
     */
    public record UpdateProfileRequest(
            @Pattern(regexp = ".*\\S.*", message = "must not be blank") @Size(max = 255) String name,
            @Pattern(regexp = ".*\\S.*", message = "must not be blank") @Email @Size(max = 255) String email) {}

    /**
     * {@code newPassword} follows the same rules as {@link RegisterRequest#password()}. A wrong
     * {@code currentPassword} yields 400 (never 401, which would log the user out on the frontend).
     */
    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {}

    public record LoginResponse(Long id, String name, String email, UserRole role, String token, Instant expiresAt) {
        static LoginResponse from(User user, TokenService.IssuedToken issuedToken) {
            return new LoginResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(),
                    issuedToken.value(), issuedToken.expiresAt());
        }
    }

    public record UserResponse(Long id, String name, String email, UserRole role, Instant createdAt) {
        static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(),
                    user.getCreatedAt());
        }
    }
}
