package com.mercatto.users.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercatto.users.domain.User;
import com.mercatto.users.domain.UserRole;
import com.mercatto.users.service.AuthenticatedUser;
import com.mercatto.users.service.EmailAlreadyExistsException;
import com.mercatto.users.service.TokenService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                .andExpect(jsonPath("$.id").value(1));

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
}
