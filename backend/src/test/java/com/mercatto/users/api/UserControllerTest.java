package com.mercatto.users.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercatto.users.domain.User;
import com.mercatto.users.domain.UserRole;
import com.mercatto.users.service.AuthenticatedUser;
import com.mercatto.users.service.EmailAlreadyExistsException;
import com.mercatto.users.service.InvalidCurrentPasswordException;
import com.mercatto.users.service.TokenService;
import com.mercatto.users.service.UserNotFoundException;
import com.mercatto.users.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    private static final AuthenticatedUser OWNER = new AuthenticatedUser(1L, UserRole.BUYER);
    private static final AuthenticatedUser OTHER_USER = new AuthenticatedUser(2L, UserRole.BUYER);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private TokenService tokenService;

    @Test
    void loginWithValidCredentials_returns200WithTokenAndWithoutPasswordHash() throws Exception {
        User user = User.builder()
                .id(1L)
                .name("Jane Doe")
                .email("jane@example.com")
                .passwordHash("$2a$10$secretHashShouldNotLeak")
                .role(UserRole.BUYER)
                .build();
        when(userService.authenticate("jane@example.com", "correct-password")).thenReturn(Optional.of(user));
        Instant expiresAt = Instant.now().plusSeconds(3600);
        when(tokenService.issue(any(Long.class), any(UserRole.class)))
                .thenReturn(new TokenService.IssuedToken("fake-jwt-token", expiresAt));

        mockMvc.perform(post("/api/users/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new UserController.LoginRequest("jane@example.com", "correct-password"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("jane@example.com"))
                .andExpect(jsonPath("$.token").value("fake-jwt-token"))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("passwordHash"))));

        verify(tokenService).issue(1L, UserRole.BUYER);
    }

    @Test
    void registerWithExistingEmail_returns409() throws Exception {
        when(userService.register(anyString(), anyString(), anyString(), any(UserRole.class)))
                .thenThrow(new EmailAlreadyExistsException("E-mail já cadastrado: jane@example.com"));

        mockMvc.perform(post("/api/users/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new UserController.RegisterRequest("Jane Doe", "jane@example.com", "super-secret-123", UserRole.BUYER))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("E-mail já cadastrado: jane@example.com"));
    }

    @Test
    void loginWithInvalidCredentials_returns401() throws Exception {
        when(userService.authenticate(anyString(), anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/users/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new UserController.LoginRequest("jane@example.com", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
    }

    @Test
    void registerWithValidRequest_returns200() throws Exception {
        User user = User.builder()
                .id(1L)
                .name("Jane Doe")
                .email("jane@example.com")
                .passwordHash("$2a$10$secretHashShouldNotLeak")
                .role(UserRole.BUYER)
                .build();
        when(userService.register(anyString(), anyString(), anyString(), any(UserRole.class))).thenReturn(user);

        mockMvc.perform(post("/api/users/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new UserController.RegisterRequest("Jane Doe", "jane@example.com", "password123", UserRole.BUYER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("passwordHash"))));

        verify(userService).register("Jane Doe", "jane@example.com", "password123", UserRole.BUYER);
    }

    static Stream<UserController.RegisterRequest> invalidRegisterRequests() {
        return Stream.of(
                // name blank
                new UserController.RegisterRequest("", "jane@example.com", "password123", UserRole.BUYER),
                // email invalid
                new UserController.RegisterRequest("Jane Doe", "not-an-email", "password123", UserRole.BUYER),
                // password too short
                new UserController.RegisterRequest("Jane Doe", "jane@example.com", "short", UserRole.BUYER),
                // role null
                new UserController.RegisterRequest("Jane Doe", "jane@example.com", "password123", null)
        );
    }

    @ParameterizedTest
    @MethodSource("invalidRegisterRequests")
    void registerWithInvalidRequest_returns400(UserController.RegisterRequest request) throws Exception {
        mockMvc.perform(post("/api/users/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    void getByIdAsOwner_returns200WithoutPasswordHash() throws Exception {
        User user = User.builder()
                .id(1L)
                .name("Jane Doe")
                .email("jane@example.com")
                .passwordHash("$2a$10$secretHashShouldNotLeak")
                .role(UserRole.BUYER)
                .build();
        when(userService.findById(1L)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/users/1").principal(OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("passwordHash"))));
    }

    @Test
    void getByIdAsOtherUser_returns403() throws Exception {
        mockMvc.perform(get("/api/users/1").principal(OTHER_USER))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }

    @Test
    void getByIdWhenNotFound_returns404() throws Exception {
        when(userService.findById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/users/1").principal(OWNER))
                .andExpect(status().isNotFound());
    }

    private static User ownerUser(String name, String email) {
        return User.builder()
                .id(1L)
                .name(name)
                .email(email)
                .passwordHash("$2a$10$secretHashShouldNotLeak")
                .role(UserRole.BUYER)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }

    @Test
    void getMe_returns200WithOwnProfileAndWithoutPasswordHash() throws Exception {
        when(userService.findById(1L)).thenReturn(Optional.of(ownerUser("Jane Doe", "jane@example.com")));

        mockMvc.perform(get("/api/users/me").principal(OWNER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Jane Doe"))
                .andExpect(jsonPath("$.email").value("jane@example.com"))
                .andExpect(jsonPath("$.role").value("BUYER"))
                .andExpect(jsonPath("$.createdAt").value("2026-01-01T00:00:00Z"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("passwordHash"))));

        verify(userService).findById(1L);
    }

    @Test
    void getMeWhenUserMissing_returns404() throws Exception {
        when(userService.findById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/users/me").principal(OWNER))
                .andExpect(status().isNotFound());
    }

    @Test
    void patchMe_updatesNameAndEmail_returns200() throws Exception {
        when(userService.updateProfile(1L, "New Name", "new@example.com"))
                .thenReturn(ownerUser("New Name", "new@example.com"));

        mockMvc.perform(patch("/api/users/me")
                        .principal(OWNER)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new UserController.UpdateProfileRequest("New Name", "new@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.createdAt").value("2026-01-01T00:00:00Z"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("passwordHash"))));

        verify(userService).updateProfile(1L, "New Name", "new@example.com");
    }

    @Test
    void patchMeWithOnlyName_passesNullEmail() throws Exception {
        when(userService.updateProfile(1L, "New", null)).thenReturn(ownerUser("New", "jane@example.com"));

        mockMvc.perform(patch("/api/users/me")
                        .principal(OWNER)
                        .contentType("application/json")
                        .content("{\"name\":\"New\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New"))
                .andExpect(jsonPath("$.email").value("jane@example.com"));

        verify(userService).updateProfile(1L, "New", null);
    }

    @Test
    void patchMeWithEmptyBody_isNoOpAndReturns200() throws Exception {
        when(userService.updateProfile(1L, null, null)).thenReturn(ownerUser("Jane Doe", "jane@example.com"));

        mockMvc.perform(patch("/api/users/me")
                        .principal(OWNER)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Jane Doe"));

        verify(userService).updateProfile(1L, null, null);
    }

    @Test
    void patchMeWithEmailOfAnotherUser_returns409() throws Exception {
        when(userService.updateProfile(1L, null, "taken@example.com"))
                .thenThrow(new EmailAlreadyExistsException("E-mail já cadastrado: taken@example.com"));

        mockMvc.perform(patch("/api/users/me")
                        .principal(OWNER)
                        .contentType("application/json")
                        .content("{\"email\":\"taken@example.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("E-mail já cadastrado: taken@example.com"));
    }

    @Test
    void patchMeWhenUserMissing_returns404() throws Exception {
        when(userService.updateProfile(1L, "New", null))
                .thenThrow(new UserNotFoundException("Usuário não encontrado: 1"));

        mockMvc.perform(patch("/api/users/me")
                        .principal(OWNER)
                        .contentType("application/json")
                        .content("{\"name\":\"New\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Usuário não encontrado: 1"));
    }

    static Stream<UserController.UpdateProfileRequest> invalidUpdateProfileRequests() {
        return Stream.of(
                // name empty
                new UserController.UpdateProfileRequest("", null),
                // name whitespace only
                new UserController.UpdateProfileRequest("   ", null),
                // email empty
                new UserController.UpdateProfileRequest(null, ""),
                // email invalid
                new UserController.UpdateProfileRequest(null, "not-an-email"),
                // name too long
                new UserController.UpdateProfileRequest("a".repeat(256), null),
                // email too long (256 chars, otherwise well-formed)
                new UserController.UpdateProfileRequest(null,
                        "a".repeat(60) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(63) + ".com")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidUpdateProfileRequests")
    void patchMeWithInvalidRequest_returns400(UserController.UpdateProfileRequest request) throws Exception {
        mockMvc.perform(patch("/api/users/me")
                        .principal(OWNER)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    private static final String CURRENT_PASSWORD = "current-secret-123";
    private static final String NEW_PASSWORD = "brand-new-secret-456";

    private String changePasswordBody(String currentPassword, String newPassword) throws Exception {
        return objectMapper.writeValueAsString(new UserController.ChangePasswordRequest(currentPassword, newPassword));
    }

    @Test
    void putMePassword_withValidRequest_returns204() throws Exception {
        mockMvc.perform(put("/api/users/me/password")
                        .principal(OWNER)
                        .contentType("application/json")
                        .content(changePasswordBody(CURRENT_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(userService).changePassword(1L, CURRENT_PASSWORD, NEW_PASSWORD);
    }

    @Test
    void putMePassword_withWrongCurrent_returns400NotUnauthorized() throws Exception {
        doThrow(new InvalidCurrentPasswordException("Senha atual incorreta"))
                .when(userService).changePassword(1L, "wrong-password", NEW_PASSWORD);

        mockMvc.perform(put("/api/users/me/password")
                        .principal(OWNER)
                        .contentType("application/json")
                        .content(changePasswordBody("wrong-password", NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(status().is(org.hamcrest.Matchers.not(401)))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Senha atual incorreta"));
    }

    @Test
    void putMePassword_withNewEqualToCurrent_returns400() throws Exception {
        doThrow(new IllegalArgumentException("A nova senha deve ser diferente da senha atual"))
                .when(userService).changePassword(1L, CURRENT_PASSWORD, CURRENT_PASSWORD);

        mockMvc.perform(put("/api/users/me/password")
                        .principal(OWNER)
                        .contentType("application/json")
                        .content(changePasswordBody(CURRENT_PASSWORD, CURRENT_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A nova senha deve ser diferente da senha atual"));
    }

    @Test
    void putMePassword_whenUserMissing_returns404() throws Exception {
        doThrow(new UserNotFoundException("Usuário não encontrado: 1"))
                .when(userService).changePassword(1L, CURRENT_PASSWORD, NEW_PASSWORD);

        mockMvc.perform(put("/api/users/me/password")
                        .principal(OWNER)
                        .contentType("application/json")
                        .content(changePasswordBody(CURRENT_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Usuário não encontrado: 1"));
    }

    static Stream<UserController.ChangePasswordRequest> invalidChangePasswordRequests() {
        return Stream.of(
                // new password too short
                new UserController.ChangePasswordRequest(CURRENT_PASSWORD, "short"),
                // new password too long (73 chars)
                new UserController.ChangePasswordRequest(CURRENT_PASSWORD, "a".repeat(73)),
                // new password blank
                new UserController.ChangePasswordRequest(CURRENT_PASSWORD, "        "),
                // new password null
                new UserController.ChangePasswordRequest(CURRENT_PASSWORD, null),
                // current password blank
                new UserController.ChangePasswordRequest("", NEW_PASSWORD),
                // current password null
                new UserController.ChangePasswordRequest(null, NEW_PASSWORD)
        );
    }

    @ParameterizedTest
    @MethodSource("invalidChangePasswordRequests")
    void putMePassword_withInvalidRequest_returns400(UserController.ChangePasswordRequest request) throws Exception {
        mockMvc.perform(put("/api/users/me/password")
                        .principal(OWNER)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }
}
