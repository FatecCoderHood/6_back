package com.enersight.auth.controller;

import com.enersight.auth.dto.LoginResponseDto;
import com.enersight.auth.security.SecurityConfig;
import com.enersight.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-at-least-32-bytes-long-for-hs256",
        "jwt.expiration-minutes=60"
})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void returnsTokenForValidCredentials() throws Exception {
        LoginResponseDto response = LoginResponseDto.builder()
                .token("signed-jwt")
                .user(LoginResponseDto.UserSummary.builder()
                        .id(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                        .email("admin@tecsys.com")
                        .roles(List.of("ADMIN"))
                        .build())
                .build();
        when(authService.login(any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"admin@tecsys.com\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("signed-jwt"))
                .andExpect(jsonPath("$.user.email").value("admin@tecsys.com"));
    }

    @Test
    void returnsUnauthorizedForBadCredentials() throws Exception {
        when(authService.login(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"admin@tecsys.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsBadRequestForMalformedBody() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("not-json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refreshReturnsNewTokenPair() throws Exception {
        LoginResponseDto response = LoginResponseDto.builder()
                .token("new-jwt")
                .refreshToken("new-refresh-token")
                .user(LoginResponseDto.UserSummary.builder()
                        .id(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                        .email("admin@tecsys.com")
                        .roles(List.of("ADMIN"))
                        .build())
                .build();
        when(authService.refresh("old-refresh-token")).thenReturn(response);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"old-refresh-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("new-jwt"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"));
    }

    @Test
    void refreshRejectsInvalidToken() throws Exception {
        when(authService.refresh("bad-token"))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"bad-token\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutReturnsNoContent() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"any-token\"}"))
                .andExpect(status().isNoContent());
    }
}
