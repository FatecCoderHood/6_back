package com.enersight.service;

import com.enersight.dto.LoginRequestDto;
import com.enersight.entity.User;
import com.enersight.entity.enums.AuditAction;
import com.enersight.entity.enums.UserRole;
import com.enersight.entity.enums.UserStatus;
import com.enersight.exception.InvalidCredentialsException;
import com.enersight.exception.UserInactiveException;
import com.enersight.exception.UserPendingException;
import com.enersight.exception.UserRejectedException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock UserDetailsServiceImpl userDetailsService;
    @Mock UserService userService;
    @Mock AuditService auditService;
    @InjectMocks AuthService service;

    private final UUID id = UUID.randomUUID();

    private LoginRequestDto request() {
        LoginRequestDto dto = new LoginRequestDto();
        dto.setEmail("n@example.test");
        dto.setPassword("password-1");
        return dto;
    }

    private User userWithStatus(UserStatus status, boolean active) {
        return User.builder().id(id).email("n@example.test").password("hash")
                .role(UserRole.USER).status(status).active(active).build();
    }

    private void givenUser(User user) {
        when(userRepository.findByEmail("n@example.test")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password-1", "hash")).thenReturn(true);
    }

    @Test
    void pendingUserCannotLogIn() {
        givenUser(userWithStatus(UserStatus.PENDING, true));

        assertThatThrownBy(() -> service.login(request())).isInstanceOf(UserPendingException.class);
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void rejectedUserCannotLogIn() {
        givenUser(userWithStatus(UserStatus.REJECTED, true));

        assertThatThrownBy(() -> service.login(request())).isInstanceOf(UserRejectedException.class);
    }

    @Test
    void anonymizedOrDeactivatedUserCannotLogIn() {
        givenUser(userWithStatus(UserStatus.ANONYMIZED, false));

        assertThatThrownBy(() -> service.login(request())).isInstanceOf(UserInactiveException.class);
    }

    @Test
    void wrongPasswordIsAuditedAndRejected() {
        when(userRepository.findByEmail("n@example.test")).thenReturn(Optional.of(userWithStatus(UserStatus.ACTIVE, true)));
        when(passwordEncoder.matches("password-1", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(request())).isInstanceOf(InvalidCredentialsException.class);
        verify(auditService).record(null, AuditAction.LOGIN_FAILURE, id);
    }

    @Test
    void unknownEmailIsAuditedWithoutTarget() {
        when(userRepository.findByEmail("n@example.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(request())).isInstanceOf(InvalidCredentialsException.class);
        verify(auditService).record(null, AuditAction.LOGIN_FAILURE, null);
    }

    @Test
    void activeUserGetsTokenAndSuccessIsAudited() {
        User user = userWithStatus(UserStatus.ACTIVE, true);
        givenUser(user);
        when(jwtService.generateToken(any())).thenReturn("token");

        assertThat(service.login(request()).getToken()).isEqualTo("token");
        verify(auditService).record(id, AuditAction.LOGIN_SUCCESS, id);
    }
}
