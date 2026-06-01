package com.enersight.controller;

import com.enersight.dto.UserPrincipal;
import com.enersight.dto.UserRegisterRequestDto;
import com.enersight.dto.UserResponseDto;
import com.enersight.dto.UserTermsHistoryDto;
import com.enersight.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService service;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponseDto register(@Valid @RequestBody UserRegisterRequestDto dto) {
        return service.register(dto);
    }

    @GetMapping("/{id}")
    public UserResponseDto findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PatchMapping("/{id}/approve-access")
    public UserResponseDto approveAccess(@PathVariable UUID id) {
        return service.approveAccess(id);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        service.delete(currentUser, userId);
    }

    @GetMapping("/{id}/terms-history")
    public List<UserTermsHistoryDto> getTermsHistory(@PathVariable UUID id) {
        return service.getTermsHistory(id);
    }
}
