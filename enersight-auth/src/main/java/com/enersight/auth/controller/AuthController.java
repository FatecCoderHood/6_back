package com.enersight.auth.controller;

import com.enersight.auth.dto.LoginRequestDto;
import com.enersight.auth.dto.LoginResponseDto;
import com.enersight.auth.dto.RefreshTokenRequestDto;
import com.enersight.auth.dto.RegisterRequestDto;
import com.enersight.auth.dto.RegisterResponseDto;
import com.enersight.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public LoginResponseDto login(@RequestBody LoginRequestDto request) {
        return authService.login(request);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponseDto register(@Valid @RequestBody RegisterRequestDto request) {
        return authService.register(request);
    }

    @PostMapping("/refresh")
    public LoginResponseDto refresh(@RequestBody RefreshTokenRequestDto request) {
        return authService.refresh(request.getRefreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody RefreshTokenRequestDto request) {
        authService.logout(request.getRefreshToken());
    }
}
