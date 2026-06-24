package com.enersight.auth.controller;

import com.enersight.auth.dto.UserDto;
import com.enersight.auth.model.User;
import com.enersight.auth.security.JwtService;
import com.enersight.auth.security.SecurityConfig;
import com.enersight.auth.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the authorization boundary configured in SecurityConfig — every /api/auth/users/**
 * endpoint must reject a non-admin caller — by exercising the real SecurityConfig/JwtService
 * beans with genuinely signed tokens, not just mocking past them.
 */
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-at-least-32-bytes-long-for-hs256",
        "jwt.expiration-minutes=60"
})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UserService userService;

    private String tokenWithRole(String role) {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("caller@tecsys.com")
                .roles(new String[]{role})
                .approved(true)
                .build();
        return jwtService.generateToken(user);
    }

    private String adminToken() {
        return tokenWithRole("ADMIN");
    }

    private String userToken() {
        return tokenWithRole("USER");
    }

    @Test
    void listAllSucceedsForAdmin() throws Exception {
        when(userService.listAll()).thenReturn(List.of(
                UserDto.builder().id(UUID.randomUUID()).email("a@tecsys.com").roles(List.of("USER")).approved(true).build()
        ));

        mockMvc.perform(get("/api/auth/users").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
    }

    @Test
    void listAllForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(get("/api/auth/users").header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listAllUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/api/auth/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType("application/json")
                        .content("{\"email\":\"new@tecsys.com\",\"password\":\"plain-password\",\"roles\":[\"USER\"]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createSucceedsForAdmin() throws Exception {
        when(userService.create(any())).thenReturn(
                UserDto.builder().id(UUID.randomUUID()).email("new@tecsys.com").roles(List.of("USER")).approved(true).build()
        );

        mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json")
                        .content("{\"email\":\"new@tecsys.com\",\"password\":\"plain-password\",\"roles\":[\"USER\"]}"))
                .andExpect(status().isCreated());
    }

    @Test
    void updateRolesForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(patch("/api/auth/users/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + userToken())
                        .contentType("application/json")
                        .content("{\"roles\":[\"ADMIN\"]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void approveForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(patch("/api/auth/users/" + UUID.randomUUID() + "/approve")
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(delete("/api/auth/users/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isForbidden());
    }
}
