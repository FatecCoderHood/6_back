package com.enersight.auth.service;

import com.enersight.auth.dto.CreateUserRequestDto;
import com.enersight.auth.dto.UpdateRolesRequestDto;
import com.enersight.auth.dto.UserDto;
import com.enersight.auth.model.User;
import com.enersight.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User user(UUID id, String email, boolean approved, String... roles) {
        return User.builder()
                .id(id)
                .email(email)
                .password("hashed-password")
                .roles(roles)
                .approved(approved)
                .build();
    }

    @Test
    void listsAllUsers() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(userRepository.findAll()).thenReturn(List.of(
                user(id1, "a@tecsys.com", true, "ADMIN"),
                user(id2, "b@tecsys.com", false, "USER")
        ));

        List<UserDto> result = userService.listAll();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(UserDto::getEmail).containsExactly("a@tecsys.com", "b@tecsys.com");
        assertThat(result).extracting(UserDto::isApproved).containsExactly(true, false);
    }

    @Test
    void createForcesApprovedTrue() {
        when(userRepository.findByEmail("new@tecsys.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("plain-password")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateUserRequestDto request = new CreateUserRequestDto("new@tecsys.com", "plain-password", List.of("ADMIN"));
        UserDto result = userService.create(request);

        assertThat(result.isApproved()).isTrue();
        assertThat(result.getRoles()).containsExactly("ADMIN");

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().isApproved()).isTrue();
        assertThat(savedUser.getValue().getPassword()).isEqualTo("hashed-password");
    }

    @Test
    void createRejectsDuplicateEmail() {
        when(userRepository.findByEmail("dup@tecsys.com")).thenReturn(Optional.of(user(UUID.randomUUID(), "dup@tecsys.com", true, "USER")));

        CreateUserRequestDto request = new CreateUserRequestDto("dup@tecsys.com", "plain-password", List.of("USER"));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> userService.create(request));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(userRepository, never()).save(any());
    }

    @Test
    void approveSetsApprovedTrue() {
        UUID id = UUID.randomUUID();
        User existing = user(id, "pending@tecsys.com", false, "USER");
        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserDto result = userService.approve(id);

        assertThat(result.isApproved()).isTrue();
    }

    @Test
    void approveThrowsNotFoundForUnknownId() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> userService.approve(id));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateRolesReplacesRoleArray() {
        UUID id = UUID.randomUUID();
        User existing = user(id, "user@tecsys.com", true, "USER");
        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserDto result = userService.updateRoles(id, new UpdateRolesRequestDto(List.of("ADMIN")));

        assertThat(result.getRoles()).containsExactly("ADMIN");
    }

    @Test
    void deleteRemovesExistingUser() {
        UUID id = UUID.randomUUID();
        when(userRepository.existsById(id)).thenReturn(true);

        userService.delete(id);

        verify(userRepository).deleteById(id);
    }

    @Test
    void deleteThrowsNotFoundForUnknownId() {
        UUID id = UUID.randomUUID();
        when(userRepository.existsById(id)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> userService.delete(id));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(userRepository, never()).deleteById(any());
    }
}
