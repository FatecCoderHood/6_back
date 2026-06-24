package com.enersight.auth.controller;

import com.enersight.auth.dto.CreateUserRequestDto;
import com.enersight.auth.dto.UpdateRolesRequestDto;
import com.enersight.auth.dto.UserDto;
import com.enersight.auth.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Admin-only user management. Every endpoint here requires ROLE_ADMIN (enforced in
 * SecurityConfig) — kept in a separate controller from AuthController so the boundary between
 * public self-service and admin-only management stays visible in the class structure.
 */
@RestController
@RequestMapping("/api/auth/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public List<UserDto> listAll() {
        return userService.listAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto create(@Valid @RequestBody CreateUserRequestDto request) {
        return userService.create(request);
    }

    @PatchMapping("/{id}")
    public UserDto updateRoles(@PathVariable UUID id, @Valid @RequestBody UpdateRolesRequestDto request) {
        return userService.updateRoles(id, request);
    }

    @PatchMapping("/{id}/approve")
    public UserDto approve(@PathVariable UUID id) {
        return userService.approve(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        userService.delete(id);
    }
}
