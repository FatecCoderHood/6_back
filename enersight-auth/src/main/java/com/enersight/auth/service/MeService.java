package com.enersight.auth.service;

import com.enersight.auth.dto.ChangePasswordRequestDto;
import com.enersight.auth.dto.MeResponseDto;
import com.enersight.auth.dto.UpdateProfileRequestDto;
import com.enersight.auth.model.User;
import com.enersight.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MeService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public MeResponseDto getProfile(Jwt jwt) {
        return toDto(currentUser(jwt));
    }

    public MeResponseDto updateProfile(Jwt jwt, UpdateProfileRequestDto request) {
        User user = currentUser(jwt);
        if (request.getName() != null) {
            user.setName(request.getName());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        return toDto(userRepository.save(user));
    }

    public void changePassword(Jwt jwt, ChangePasswordRequestDto request) {
        User user = currentUser(jwt);
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    public void deleteSelf(Jwt jwt) {
        userRepository.delete(currentUser(jwt));
    }

    private User currentUser(Jwt jwt) {
        UUID id = UUID.fromString(jwt.getSubject());
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private MeResponseDto toDto(User user) {
        return MeResponseDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .phone(user.getPhone())
                .roles(List.of(user.getRoles()))
                .approved(user.isApproved())
                .build();
    }
}
