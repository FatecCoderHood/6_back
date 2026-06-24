package com.enersight.auth.service;

import com.enersight.auth.dto.CreateUserRequestDto;
import com.enersight.auth.dto.UpdateRolesRequestDto;
import com.enersight.auth.dto.UserDto;
import com.enersight.auth.model.User;
import com.enersight.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public List<UserDto> listAll() {
        return userRepository.findAll().stream().map(this::toDto).toList();
    }

    public UserDto create(CreateUserRequestDto request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .roles(request.getRoles().toArray(new String[0]))
                .approved(true)
                .build();

        return toDto(userRepository.save(user));
    }

    public UserDto updateRoles(UUID id, UpdateRolesRequestDto request) {
        User user = findOrThrow(id);
        user.setRoles(request.getRoles().toArray(new String[0]));
        return toDto(userRepository.save(user));
    }

    public UserDto approve(UUID id) {
        User user = findOrThrow(id);
        user.setApproved(true);
        return toDto(userRepository.save(user));
    }

    public void delete(UUID id) {
        if (!userRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        userRepository.deleteById(id);
    }

    private User findOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private UserDto toDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .roles(List.of(user.getRoles()))
                .approved(user.isApproved())
                .build();
    }
}
