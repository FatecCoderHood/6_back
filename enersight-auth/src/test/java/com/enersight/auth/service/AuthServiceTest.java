package com.enersight.auth.service;

import com.enersight.auth.dto.LoginRequestDto;
import com.enersight.auth.dto.LoginResponseDto;
import com.enersight.auth.dto.RegisterRequestDto;
import com.enersight.auth.dto.RegisterResponseDto;
import com.enersight.auth.model.RefreshToken;
import com.enersight.auth.model.User;
import com.enersight.auth.repository.UserRepository;
import com.enersight.auth.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

    private User existingUser(boolean approved) {
        return User.builder()
                .id(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .email("test@tecsys.com")
                .password("hashed-password")
                .roles(new String[]{"ADMIN"})
                .approved(approved)
                .build();
    }

    @Test
    void issuesTokenForCorrectCredentials() {
        User user = existingUser(true);
        when(userRepository.findByEmail("test@tecsys.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct-password", "hashed-password")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("signed-jwt");
        when(refreshTokenService.issue(user)).thenReturn("raw-refresh-token");

        LoginResponseDto response = authService.login(new LoginRequestDto("test@tecsys.com", "correct-password"));

        assertThat(response.getToken()).isEqualTo("signed-jwt");
        assertThat(response.getRefreshToken()).isEqualTo("raw-refresh-token");
        assertThat(response.getUser().getId()).isEqualTo(user.getId());
        assertThat(response.getUser().getEmail()).isEqualTo("test@tecsys.com");
        assertThat(response.getUser().getRoles()).containsExactly("ADMIN");
        // name is optional and unset for this fixture — must not break login.
        assertThat(response.getUser().getName()).isNull();
    }

    @Test
    void loginIncludesStoredNameWhenPresent() {
        User user = existingUser(true);
        user.setName("Test User");
        when(userRepository.findByEmail("test@tecsys.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct-password", "hashed-password")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("signed-jwt");
        when(refreshTokenService.issue(user)).thenReturn("raw-refresh-token");

        LoginResponseDto response = authService.login(new LoginRequestDto("test@tecsys.com", "correct-password"));

        assertThat(response.getUser().getName()).isEqualTo("Test User");
    }

    @Test
    void refreshRotatesTokenAndIssuesNewAccessToken() {
        User user = existingUser(true);
        RefreshToken stored = RefreshToken.builder().userId(user.getId()).build();
        when(refreshTokenService.validate("old-raw-token")).thenReturn(stored);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(refreshTokenService.revokeIfActive("old-raw-token")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("new-jwt");
        when(refreshTokenService.issue(user)).thenReturn("new-raw-token");

        LoginResponseDto response = authService.refresh("old-raw-token");

        assertThat(response.getToken()).isEqualTo("new-jwt");
        assertThat(response.getRefreshToken()).isEqualTo("new-raw-token");
        assertThat(response.getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    void refreshRejectsWhenUserNoLongerExists() {
        RefreshToken stored = RefreshToken.builder().userId(UUID.randomUUID()).build();
        when(refreshTokenService.validate("old-raw-token")).thenReturn(stored);
        when(userRepository.findById(stored.getUserId())).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.refresh("old-raw-token"));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refreshPropagatesInvalidTokenRejection() {
        when(refreshTokenService.validate("bad-token"))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        assertThrows(ResponseStatusException.class, () -> authService.refresh("bad-token"));
    }

    @Test
    void refreshRejectsTheLoserOfAConcurrentReplay() {
        // Two requests racing on the same refresh token both pass validate() (neither has
        // rotated yet); revokeIfActive() is the atomic CAS that decides which one actually wins.
        User user = existingUser(true);
        RefreshToken stored = RefreshToken.builder().userId(user.getId()).build();
        when(refreshTokenService.validate("raced-token")).thenReturn(stored);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(refreshTokenService.revokeIfActive("raced-token")).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.refresh("raced-token"));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void logoutDelegatesToRefreshTokenServiceRevoke() {
        authService.logout("raw-refresh-token");

        verify(refreshTokenService).revoke("raw-refresh-token");
    }

    @Test
    void rejectsWrongPassword() {
        when(userRepository.findByEmail("test@tecsys.com")).thenReturn(Optional.of(existingUser(true)));
        when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.login(new LoginRequestDto("test@tecsys.com", "wrong-password")));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsUnknownOrDeletedEmail() {
        // No "inactive" state exists: a deactivated user's row is physically deleted (LGPD
        // erasure), so this is the same path as an email that never existed.
        when(userRepository.findByEmail("gone@tecsys.com")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.login(new LoginRequestDto("gone@tecsys.com", "any-password")));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsCorrectCredentialsForUnapprovedAccount() {
        when(userRepository.findByEmail("test@tecsys.com")).thenReturn(Optional.of(existingUser(false)));
        when(passwordEncoder.matches("correct-password", "hashed-password")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.login(new LoginRequestDto("test@tecsys.com", "correct-password")));

        // Distinguishable from a wrong/unknown credential (401): this account exists and the
        // password is correct, it's just not approved yet.
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void registerCreatesUnapprovedUserWithUserRoleOnly() {
        when(userRepository.findByEmail("new@tecsys.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("plain-password")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegisterResponseDto response = authService.register(
                RegisterRequestDto.builder().email("new@tecsys.com").password("plain-password").build());

        assertThat(response.getEmail()).isEqualTo("new@tecsys.com");
        assertThat(response.isApproved()).isFalse();

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getRoles()).containsExactly("USER");
        assertThat(savedUser.getValue().isApproved()).isFalse();
        assertThat(savedUser.getValue().getPassword()).isEqualTo("hashed-password");
        // name/phone are optional — omitting them must not break registration.
        assertThat(savedUser.getValue().getName()).isNull();
        assertThat(savedUser.getValue().getPhone()).isNull();
    }

    @Test
    void registerStoresOptionalNameAndPhoneWhenProvided() {
        when(userRepository.findByEmail("new@tecsys.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("plain-password")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(RegisterRequestDto.builder()
                .email("new@tecsys.com")
                .password("plain-password")
                .name("New User")
                .phone("(11) 99999-0000")
                .build());

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getName()).isEqualTo("New User");
        assertThat(savedUser.getValue().getPhone()).isEqualTo("(11) 99999-0000");
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.findByEmail("test@tecsys.com")).thenReturn(Optional.of(existingUser(true)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.register(
                        RegisterRequestDto.builder().email("test@tecsys.com").password("plain-password").build()));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
