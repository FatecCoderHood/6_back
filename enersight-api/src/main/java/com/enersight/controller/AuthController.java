package com.enersight.controller;

import com.enersight.dto.LoginRequestDto;
import com.enersight.dto.LoginResponseDto;
import com.enersight.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService service;

    @PostMapping("/login")
    public LoginResponseDto login(@Valid @RequestBody LoginRequestDto dto) {
        return service.login(dto);
    }
}
