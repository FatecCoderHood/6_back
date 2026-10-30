package com.enersight.controller;

import com.enersight.dto.RejectUserRequestDto;
import com.enersight.dto.UserPrincipal;
import com.enersight.dto.UserRegisterRequestDto;
import com.enersight.dto.UserResponseDto;
import com.enersight.dto.UserTermsHistoryDto;
import com.enersight.entity.enums.UserStatus;
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

    @GetMapping
    public List<UserResponseDto> list(
            @RequestParam(required = false) UserStatus status,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return service.list(currentUser, status);
    }

    @GetMapping("/{id}")
    public UserResponseDto findById(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal currentUser) {
        return service.findById(currentUser, id);
    }

    @PatchMapping("/{id}/approve-access")
    public UserResponseDto approveAccess(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal currentUser) {
        return service.approveAccess(currentUser, id);
    }

    @PatchMapping("/{id}/reject")
    public UserResponseDto reject(
            @PathVariable UUID id,
            @Valid @RequestBody RejectUserRequestDto dto,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return service.reject(currentUser, id, dto.getReason());
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe(@AuthenticationPrincipal UserPrincipal currentUser) {
        service.anonymize(currentUser, currentUser.getId());
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        service.anonymize(currentUser, userId);
    }

    @GetMapping("/{id}/terms-history")
    public List<UserTermsHistoryDto> getTermsHistory(
            @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal currentUser) {
        return service.getTermsHistory(currentUser, id);
    }
}
