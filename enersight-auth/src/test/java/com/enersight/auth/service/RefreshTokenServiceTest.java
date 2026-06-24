package com.enersight.auth.service;

import com.enersight.auth.model.RefreshToken;
import com.enersight.auth.model.User;
import com.enersight.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
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
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setRefreshExpiration() {
        ReflectionTestUtils.setField(refreshTokenService, "refreshExpirationDays", 30L);
    }

    private User user() {
        return User.builder()
                .id(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .email("test@tecsys.com")
                .roles(new String[]{"USER"})
                .build();
    }

    @Test
    void issueStoresHashedTokenNotTheRawValue() {
        when(refreshTokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        String raw = refreshTokenService.issue(user());

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(raw).isNotBlank();
        assertThat(captor.getValue().getTokenHash()).isNotEqualTo(raw);
        assertThat(captor.getValue().getUserId()).isEqualTo(user().getId());
        assertThat(captor.getValue().isRevoked()).isFalse();
        assertThat(captor.getValue().getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void validateAcceptsTheTokenItJustIssued() {
        when(refreshTokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        String raw = refreshTokenService.issue(user());

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken stored = captor.getValue();
        when(refreshTokenRepository.findByTokenHash(stored.getTokenHash())).thenReturn(Optional.of(stored));

        assertThat(refreshTokenService.validate(raw)).isEqualTo(stored);
    }

    @Test
    void validateRejectsUnknownToken() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> refreshTokenService.validate("unknown-token"));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validateRejectsRevokedToken() {
        RefreshToken revoked = RefreshToken.builder()
                .tokenHash("irrelevant-since-lookup-is-mocked")
                .revoked(true)
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(revoked));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> refreshTokenService.validate("any-raw-value"));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validateRejectsExpiredToken() {
        RefreshToken expired = RefreshToken.builder()
                .tokenHash("irrelevant-since-lookup-is-mocked")
                .revoked(false)
                .expiresAt(Instant.now().minusSeconds(60))
                .build();
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(expired));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> refreshTokenService.validate("any-raw-value"));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void revokeMarksTheMatchingTokenRevoked() {
        RefreshToken stored = RefreshToken.builder()
                .tokenHash("irrelevant-since-lookup-is-mocked")
                .revoked(false)
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(stored));
        when(refreshTokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        refreshTokenService.revoke("any-raw-value");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().isRevoked()).isTrue();
    }

    @Test
    void revokeIsNoOpForUnknownToken() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        refreshTokenService.revoke("unknown-token");

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void revokeIfActiveReturnsTrueWhenItWinsTheUpdate() {
        when(refreshTokenRepository.revokeIfActive(any())).thenReturn(1);

        assertThat(refreshTokenService.revokeIfActive("any-raw-value")).isTrue();
    }

    @Test
    void revokeIfActiveReturnsFalseWhenAlreadyRevokedByAConcurrentCaller() {
        when(refreshTokenRepository.revokeIfActive(any())).thenReturn(0);

        assertThat(refreshTokenService.revokeIfActive("any-raw-value")).isFalse();
    }
}
