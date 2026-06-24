package com.enersight.auth.controller;

import com.enersight.auth.dto.MeResponseDto;
import com.enersight.auth.model.User;
import com.enersight.auth.security.JwtService;
import com.enersight.auth.security.SecurityConfig;
import com.enersight.auth.service.MeService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/auth/me is authenticated self-service for any role — unlike UserControllerTest, there is no
 * hasRole("ADMIN") to prove; the thing worth proving is that a plain USER token (not just ADMIN)
 * gets through, and that no token at all is rejected.
 */
@WebMvcTest(MeController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-at-least-32-bytes-long-for-hs256",
        "jwt.expiration-minutes=60"
})
class MeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private MeService meService;

    private String userToken() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("caller@tecsys.com")
                .roles(new String[]{"USER"})
                .approved(true)
                .build();
        return jwtService.generateToken(user);
    }

    @Test
    void getProfileSucceedsForAuthenticatedUser() throws Exception {
        when(meService.getProfile(any())).thenReturn(
                MeResponseDto.builder().id(UUID.randomUUID()).email("caller@tecsys.com").roles(List.of("USER")).approved(true).build()
        );

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isOk());
    }

    @Test
    void getProfileUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateProfileSucceedsForAuthenticatedUser() throws Exception {
        when(meService.updateProfile(any(), any())).thenReturn(
                MeResponseDto.builder().id(UUID.randomUUID()).email("caller@tecsys.com").roles(List.of("USER")).approved(true).build()
        );

        mockMvc.perform(patch("/api/auth/me")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType("application/json")
                        .content("{\"name\":\"New Name\",\"phone\":\"(11) 90000-0000\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void changePasswordSucceedsForAuthenticatedUser() throws Exception {
        mockMvc.perform(patch("/api/auth/me/password")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"old-password\",\"newPassword\":\"new-password\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void changePasswordUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(patch("/api/auth/me/password")
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"old-password\",\"newPassword\":\"new-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteSelfSucceedsForAuthenticatedUser() throws Exception {
        mockMvc.perform(delete("/api/auth/me").header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteSelfUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(delete("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}
