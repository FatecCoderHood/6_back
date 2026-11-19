package com.enersight.controller;

import com.enersight.dto.UserPrincipal;
import com.enersight.entity.enums.UserRole;
import com.enersight.exception.GlobalExceptionHandler;
import com.enersight.exception.UserPendingException;
import com.enersight.service.AuthService;
import com.enersight.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthWebLayerTest {

    private final AuthService authService = Mockito.mock(AuthService.class);
    private final UserService userService = Mockito.mock(UserService.class);
    private final UserPrincipal principal =
            new UserPrincipal(UUID.randomUUID(), "user@example.test", "hash", UserRole.USER);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(authService), new UserController(userService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void pendingLoginReturns403WithErrorBody() throws Exception {
        when(authService.login(any())).thenThrow(new UserPendingException("User access pending approval."));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"n@example.test\",\"password\":\"password-1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("User access pending approval."));
    }

    @Test
    void accessDeniedReturns403WithErrorBody() throws Exception {
        when(userService.list(any(), any())).thenThrow(new AccessDeniedException("Admin only"));

        mvc.perform(get("/api/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void rejectWithBlankReasonReturns400() throws Exception {
        mvc.perform(patch("/api/users/" + UUID.randomUUID() + "/reject")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteMeAnonymizesTheAuthenticatedUser() throws Exception {
        mvc.perform(delete("/api/users/me")).andExpect(status().isNoContent());

        verify(userService).anonymize(principal, principal.getId());
    }
}
