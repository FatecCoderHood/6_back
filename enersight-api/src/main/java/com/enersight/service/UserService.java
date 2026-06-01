package com.enersight.service;

import com.enersight.dto.UserPrincipal;
import com.enersight.dto.UserRegisterRequestDto;
import com.enersight.dto.UserResponseDto;
import com.enersight.dto.UserTermsHistoryDto;
import com.enersight.entity.TermsOfUse;
import com.enersight.entity.User;
import com.enersight.entity.UserTermsAcceptance;
import com.enersight.entity.enums.AcceptanceStatus;
import com.enersight.entity.enums.TermType;
import com.enersight.entity.enums.UserRole;
import com.enersight.exception.MandatoryTermsNotAcceptedException;
import com.enersight.exception.ResourceNotFoundException;
import com.enersight.exception.UserAlreadyExistsException;
import com.enersight.repository.TermsOfUseRepository;
import com.enersight.repository.UserRepository;
import com.enersight.repository.UserTermsAcceptanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TermsOfUseRepository termsRepository;
    private final UserTermsAcceptanceRepository acceptanceRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponseDto register(UserRegisterRequestDto dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new UserAlreadyExistsException("Email already registered: " + dto.getEmail());
        }

        validateMandatoryTermsAccepted(dto.getAcceptedTermIds(), null);

        User user = User.builder()
                .name(dto.getName())
                .email(dto.getEmail())
                .password(passwordEncoder.encode(dto.getPassword()))
                .role(UserRole.PENDING)
                .build();
        user = userRepository.save(user);

        recordAcceptances(user, dto.getAcceptedTermIds());

        return toResponse(user);
    }

    public UserResponseDto findById(UUID id) {
        return toResponse(getOrThrow(id));
    }

    @Transactional
    public UserResponseDto approveAccess(UUID id) {
        User user = getOrThrow(id);
        user.setRole(UserRole.USER);
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public void delete(UserPrincipal currentUser, UUID targetUserId) {

        if (!currentUser.hasRole(UserRole.ADMIN)) {
            throw new AccessDeniedException("Only admins can delete users");
        }

        userRepository.deleteById(targetUserId);
    }

    public List<UserTermsHistoryDto> getTermsHistory(UUID userId) {
        getOrThrow(userId);
        return acceptanceRepository.findAllByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(a -> UserTermsHistoryDto.builder()
                        .termId(a.getTerm().getId())
                        .termTitle(a.getTerm().getTitle())
                        .termType(a.getTerm().getType())
                        .status(a.getStatus())
                        .createdAt(a.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * For registration: pass acceptedTermIds from request body, userId = null.
     * For login: pass acceptedTermIds = null, userId from the authenticated user.
     */
    public void validateMandatoryTermsAccepted(List<UUID> acceptedTermIds, UUID userId) {
        List<TermsOfUse> mandatoryTerms = termsRepository.findAllByActiveTrueAndType(TermType.MANDATORY);
        if (mandatoryTerms.isEmpty()) {
            return;
        }

        Set<UUID> acceptedSet;

        if (userId != null) {
            acceptedSet = acceptanceRepository.findLatestStatusPerTermByUserId(userId).stream()
                    .filter(a -> a.getStatus() == AcceptanceStatus.ACCEPTED)
                    .map(a -> a.getTerm().getId())
                    .collect(Collectors.toSet());
        } else {
            acceptedSet = acceptedTermIds != null ? Set.copyOf(acceptedTermIds) : Set.of();
        }

        List<UUID> missing = mandatoryTerms.stream()
                .map(TermsOfUse::getId)
                .filter(id -> !acceptedSet.contains(id))
                .collect(Collectors.toList());

        if (!missing.isEmpty()) {
            throw new MandatoryTermsNotAcceptedException(missing);
        }
    }

    private void recordAcceptances(User user, List<UUID> termIds) {
        termIds.forEach(termId -> {
            TermsOfUse term = termsRepository.findById(termId)
                    .orElseThrow(() -> new ResourceNotFoundException("Term not found: " + termId));
            acceptanceRepository.save(UserTermsAcceptance.builder()
                    .user(user)
                    .term(term)
                    .status(AcceptanceStatus.ACCEPTED)
                    .build());
        });
    }

    private User getOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    public UserResponseDto toResponse(User user) {
        return UserResponseDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
