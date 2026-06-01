package com.enersight.service;

import com.enersight.dto.LoginRequestDto;
import com.enersight.dto.LoginResponseDto;
import com.enersight.entity.User;
import com.enersight.entity.enums.UserRole;
import com.enersight.exception.InvalidCredentialsException;
import com.enersight.exception.UserInactiveException;
import com.enersight.exception.UserPendingException;
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

    public LoginResponseDto login(LoginRequestDto dto) {
        User user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        if (!user.isActive()) {
            throw new UserInactiveException();
        }

        if (user.getRole() == UserRole.PENDING) {
            throw new UserPendingException("User access pending approval. Please contact support.");
        }

        userService.validateMandatoryTermsAccepted(null, user.getId());

        String token = jwtService.generateToken(userDetailsService.loadUserByUsername(user.getEmail()));

        return LoginResponseDto.builder()
                .token(token)
                .user(userService.toResponse(user))
                .build();
    }
}
