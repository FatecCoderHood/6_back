package com.enersight.service;

import com.enersight.dto.LoginRequestDto;
import com.enersight.dto.LoginResponseDto;
import com.enersight.dto.UserResponseDto;
import com.enersight.entity.User;
import com.enersight.entity.enums.UserRole;
import com.enersight.exception.InvalidCredentialsException;
import com.enersight.exception.UserPendingException;
import com.enersight.repository.UserRepository;
import com.enersight.security.JwtService;
import com.enersight.security.UserDetailsServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    JwtService jwtService;

    @Mock
    UserDetailsServiceImpl userDetailsService;

    @Mock
    UserService userService;

    @InjectMocks
    AuthService authService;

    @Test
    void login_throwsWhenInvalidCredentials() {
        LoginRequestDto dto = new LoginRequestDto();
        dto.setEmail("noone@example.com");
        dto.setPassword("x");

        when(userRepository.findByEmail("noone@example.com")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> authService.login(dto));
    }

    @Test
    void login_throwsWhenPendingRole() {
        LoginRequestDto dto = new LoginRequestDto();
        dto.setEmail("pending@example.com");
        dto.setPassword("pw");

        User user = User.builder().id(UUID.randomUUID()).email("pending@example.com").password("enc").role(UserRole.PENDING).active(true).build();

        when(userRepository.findByEmail("pending@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "enc")).thenReturn(true);

        assertThrows(UserPendingException.class, () -> authService.login(dto));
    }

    @Test
    void login_returnsTokenAndUserOnSuccess() {
        LoginRequestDto dto = new LoginRequestDto();
        dto.setEmail("alice@example.com");
        dto.setPassword("pw");

        User user = User.builder().id(UUID.randomUUID()).email("alice@example.com").password("enc").role(UserRole.USER).active(true).build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "enc")).thenReturn(true);
        when(jwtService.generateToken(org.mockito.ArgumentMatchers.any())).thenReturn("token123");
        when(userDetailsService.loadUserByUsername("alice@example.com")).thenReturn(org.springframework.security.core.userdetails.User.withUsername("alice@example.com").password("enc").roles("USER").build());
        when(userService.toResponse(user)).thenReturn(UserResponseDto.builder().id(user.getId()).email(user.getEmail()).name("Alice").role(UserRole.USER).active(true).build());

        LoginResponseDto resp = authService.login(dto);

        assertNotNull(resp);
        assertEquals("token123", resp.getToken());
        assertEquals("alice@example.com", resp.getUser().getEmail());
    }
}
