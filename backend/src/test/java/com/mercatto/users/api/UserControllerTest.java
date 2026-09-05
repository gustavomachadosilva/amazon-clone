package com.mercatto.users.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercatto.users.domain.User;
import com.mercatto.users.domain.UserRole;
import com.mercatto.users.service.EmailAlreadyExistsException;
import com.mercatto.users.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @Test
    void loginWithValidCredentials_returns200WithoutPasswordHash() throws Exception {
        User user = User.builder()
                .id(1L)
                .name("Jane Doe")
                .email("jane@example.com")
                .passwordHash("$2a$10$secretHashShouldNotLeak")
                .role(UserRole.BUYER)
                .build();
        when(userService.authenticate("jane@example.com", "correct-password")).thenReturn(Optional.of(user));

        mockMvc.perform(post("/api/users/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new UserController.LoginRequest("jane@example.com", "correct-password"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("jane@example.com"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("passwordHash"))));
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
}
