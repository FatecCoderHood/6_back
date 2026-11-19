package com.enersight.controller;

import com.enersight.dto.ChangePasswordRequestDto;
import com.enersight.dto.UserPrincipal;
import com.enersight.entity.enums.UserRole;
import com.enersight.exception.GlobalExceptionHandler;
import com.enersight.exception.InvalidPasswordChangeException;
import com.enersight.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChangePasswordWebLayerTest {

    private static final String URL = "/api/users/me/password";

    private final UserService userService = Mockito.mock(UserService.class);
    private final UserPrincipal principal =
            new UserPrincipal(UUID.randomUUID(), "user@example.test", "hash", UserRole.USER);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new UserController(userService))
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

    private String body(String current, String next) {
        return "{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}";
    }

    @Test
    void validRequestReturns204AndChangesThePasswordOfTheAuthenticatedUser() throws Exception {
        mvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content(body("old-password", "new-password-1")))
                .andExpect(status().isNoContent());

        ArgumentCaptor<ChangePasswordRequestDto> captor = ArgumentCaptor.forClass(ChangePasswordRequestDto.class);
        verify(userService).changePassword(Mockito.eq(principal), captor.capture());
        assertThat(captor.getValue().getCurrentPassword()).isEqualTo("old-password");
        assertThat(captor.getValue().getNewPassword()).isEqualTo("new-password-1");
    }

    @Test
    void newPasswordShorterThanEightCharactersReturns400() throws Exception {
        mvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content(body("old-password", "short")))
                .andExpect(status().isBadRequest());

        verify(userService, never()).changePassword(any(), any());
    }

    @Test
    void blankCurrentPasswordReturns400() throws Exception {
        mvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content(body("  ", "new-password-1")))
                .andExpect(status().isBadRequest());

        verify(userService, never()).changePassword(any(), any());
    }

    @Test
    void wrongCurrentPasswordReturns400NotUnauthorizedSoTheSessionIsKept() throws Exception {
        doThrow(new InvalidPasswordChangeException("Current password is incorrect"))
                .when(userService).changePassword(any(), any());

        mvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content(body("wrong", "new-password-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));
    }
}
