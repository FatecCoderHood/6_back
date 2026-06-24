package com.enersight.auth.service;

import com.enersight.auth.dto.LoginRequestDto;
import com.enersight.auth.dto.LoginResponseDto;
import com.enersight.auth.dto.RegisterRequestDto;
import com.enersight.auth.dto.RegisterResponseDto;
import com.enersight.auth.model.RefreshToken;
import com.enersight.auth.model.User;
import com.enersight.auth.repository.UserRepository;
import com.enersight.auth.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public LoginResponseDto login(LoginRequestDto request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        if (!user.isApproved()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is not approved");
        }

        return buildLoginResponse(user);
    }

    public LoginResponseDto refresh(String rawRefreshToken) {
        RefreshToken refreshToken = refreshTokenService.validate(rawRefreshToken);
        User user = userRepository.findById(refreshToken.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        // Atomic CAS, not a separate read-then-write: if this same token is replayed concurrently,
        // only one caller's UPDATE actually flips revoked=false -> true. The loser is rejected here
        // instead of both callers successfully minting a new session from a single token.
        if (!refreshTokenService.revokeIfActive(rawRefreshToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }

        return buildLoginResponse(user);
    }

    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    private LoginResponseDto buildLoginResponse(User user) {
        String token = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.issue(user);

        return LoginResponseDto.builder()
                .token(token)
                .refreshToken(refreshToken)
                .user(LoginResponseDto.UserSummary.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .name(user.getName())
                        .roles(List.of(user.getRoles()))
                        .build())
                .build();
    }

    public RegisterResponseDto register(RegisterRequestDto request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .phone(request.getPhone())
                .roles(new String[]{"USER"})
                .approved(false)
                .build();
        userRepository.save(user);

        return RegisterResponseDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .approved(user.isApproved())
                .build();
    }
}
