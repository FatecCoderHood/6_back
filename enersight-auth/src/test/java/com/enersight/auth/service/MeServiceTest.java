package com.enersight.auth.service;

import com.enersight.auth.dto.ChangePasswordRequestDto;
import com.enersight.auth.dto.MeResponseDto;
import com.enersight.auth.dto.UpdateProfileRequestDto;
import com.enersight.auth.model.User;
import com.enersight.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private MeService meService;

    private final UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private Jwt jwtFor(UUID id) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("sub", id.toString())
                .subject(id.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }

    private User existingUser() {
        return User.builder()
                .id(userId)
                .email("user@tecsys.com")
                .password("hashed-password")
                .name("Original Name")
                .phone("(11) 90000-0000")
                .roles(new String[]{"USER"})
                .approved(true)
                .build();
    }

    @Test
    void getProfileReturnsCurrentUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser()));

        MeResponseDto profile = meService.getProfile(jwtFor(userId));

        assertThat(profile.getEmail()).isEqualTo("user@tecsys.com");
        assertThat(profile.getName()).isEqualTo("Original Name");
        assertThat(profile.getPhone()).isEqualTo("(11) 90000-0000");
        assertThat(profile.getRoles()).containsExactly("USER");
    }

    @Test
    void updateProfileUpdatesNameAndPhone() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser()));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MeResponseDto result = meService.updateProfile(jwtFor(userId),
                new UpdateProfileRequestDto("New Name", "(11) 91111-1111"));

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getPhone()).isEqualTo("(11) 91111-1111");
    }

    @Test
    void updateProfileLeavesFieldUnchangedWhenNull() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser()));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MeResponseDto result = meService.updateProfile(jwtFor(userId), new UpdateProfileRequestDto(null, "(11) 92222-2222"));

        assertThat(result.getName()).isEqualTo("Original Name");
        assertThat(result.getPhone()).isEqualTo("(11) 92222-2222");
    }

    @Test
    void changePasswordSucceedsWithCorrectCurrentPassword() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser()));
        when(passwordEncoder.matches("current-password", "hashed-password")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        meService.changePassword(jwtFor(userId), new ChangePasswordRequestDto("current-password", "new-password"));

        verify(userRepository).save(argThatPasswordIs("new-hashed-password"));
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser()));
        when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> meService.changePassword(jwtFor(userId), new ChangePasswordRequestDto("wrong-password", "new-password")));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(userRepository, never()).save(any());
    }

    @Test
    void deleteSelfDeletesCurrentUser() {
        User user = existingUser();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        meService.deleteSelf(jwtFor(userId));

        verify(userRepository).delete(user);
    }

    private User argThatPasswordIs(String expectedPassword) {
        return org.mockito.ArgumentMatchers.argThat(u -> u.getPassword().equals(expectedPassword));
    }
}
