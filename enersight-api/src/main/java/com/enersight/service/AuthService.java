package com.enersight.service;

import com.enersight.dto.LoginRequestDto;
import com.enersight.dto.LoginResponseDto;
import com.enersight.entity.User;
import com.enersight.entity.enums.AuditAction;
import com.enersight.entity.enums.UserStatus;
import com.enersight.exception.InvalidCredentialsException;
import com.enersight.exception.UserInactiveException;
import com.enersight.exception.UserPendingException;
import com.enersight.exception.UserRejectedException;
import com.enersight.repository.UserRepository;
import com.enersight.security.JwtService;
import com.enersight.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;
    private final UserService userService;
    private final AuditService auditService;

    public LoginResponseDto login(LoginRequestDto dto) {
        User user = userRepository.findByEmail(dto.getEmail()).orElse(null);
        if (user == null) {
            auditService.record(null, AuditAction.LOGIN_FAILURE, null);
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            auditService.record(null, AuditAction.LOGIN_FAILURE, user.getId());
            throw new InvalidCredentialsException();
        }
        if (!user.isActive()) {
            throw new UserInactiveException();
        }
        if (user.getStatus() == UserStatus.PENDING) {
            throw new UserPendingException("User access pending approval. Please contact support.");
        }
        if (user.getStatus() == UserStatus.REJECTED) {
            throw new UserRejectedException();
        }
        userService.validateMandatoryTermsAccepted(null, user.getId());
        String token = jwtService.generateToken(userDetailsService.loadUserByUsername(user.getEmail()));
        auditService.record(user.getId(), AuditAction.LOGIN_SUCCESS, user.getId());

        return LoginResponseDto.builder()
                .token(token)
                .user(userService.toResponse(user))
                .build();
    }
}
