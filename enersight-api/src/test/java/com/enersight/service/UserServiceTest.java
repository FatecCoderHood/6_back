package com.enersight.service;

import com.enersight.dto.UserRegisterRequestDto;
import com.enersight.dto.UserResponseDto;
import com.enersight.entity.TermsOfUse;
import com.enersight.entity.User;
import com.enersight.entity.enums.TermType;
import com.enersight.entity.enums.UserRole;
import com.enersight.exception.MandatoryTermsNotAcceptedException;
import com.enersight.exception.UserAlreadyExistsException;
import com.enersight.repository.TermsOfUseRepository;
import com.enersight.repository.UserRepository;
import com.enersight.repository.UserTermsAcceptanceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    TermsOfUseRepository termsRepository;

    @Mock
    UserTermsAcceptanceRepository acceptanceRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @InjectMocks
    UserService userService;

    @Test
    void toResponse_mapsFieldsCorrectly() {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        User user = User.builder()
                .id(id)
                .name("Alice")
                .email("alice@example.com")
                .role(UserRole.USER)
                .active(true)
                .createdAt(now)
                .build();

        UserResponseDto dto = userService.toResponse(user);

        assertEquals(id, dto.getId());
        assertEquals("Alice", dto.getName());
        assertEquals("alice@example.com", dto.getEmail());
        assertEquals(UserRole.USER, dto.getRole());
        assertTrue(dto.isActive());
        assertEquals(now, dto.getCreatedAt());
    }

    @Test
    void register_throwsWhenEmailAlreadyExists() {
        UserRegisterRequestDto req = new UserRegisterRequestDto();
        req.setName("Bob");
        req.setEmail("bob@example.com");
        req.setPassword("password123");
        req.setAcceptedTermIds(emptyList());

        when(userRepository.existsByEmail("bob@example.com")).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> userService.register(req));
    }

    @Test
    void validateMandatoryTermsAccepted_throwsWhenMissing() {
        UUID termId = UUID.randomUUID();
        TermsOfUse term = TermsOfUse.builder().id(termId).type(TermType.MANDATORY).build();

        when(termsRepository.findAllByActiveTrueAndType(TermType.MANDATORY)).thenReturn(List.of(term));

        // no accepted ids provided
        assertThrows(MandatoryTermsNotAcceptedException.class, () -> userService.validateMandatoryTermsAccepted(null, null));
    }
}
