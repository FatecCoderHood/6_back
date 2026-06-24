package com.enersight.auth.controller;

import com.enersight.auth.dto.ChangePasswordRequestDto;
import com.enersight.auth.dto.MeResponseDto;
import com.enersight.auth.dto.UpdateProfileRequestDto;
import com.enersight.auth.service.MeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Authenticated self-service — distinct from AuthController's public routes (login/register) and
 * UserController's admin-only routes. Any authenticated user (any role) can manage their own
 * account here; no SecurityConfig rule needed since /api/auth/me falls under the existing
 * anyRequest().authenticated() catch-all.
 */
@RestController
@RequestMapping("/api/auth/me")
@RequiredArgsConstructor
public class MeController {

    private final MeService meService;

    @GetMapping
    public MeResponseDto getProfile(@AuthenticationPrincipal Jwt jwt) {
        return meService.getProfile(jwt);
    }

    @PatchMapping
    public MeResponseDto updateProfile(@AuthenticationPrincipal Jwt jwt, @RequestBody UpdateProfileRequestDto request) {
        return meService.updateProfile(jwt, request);
    }

    @PatchMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequestDto request) {
        meService.changePassword(jwt, request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSelf(@AuthenticationPrincipal Jwt jwt) {
        meService.deleteSelf(jwt);
    }
}
