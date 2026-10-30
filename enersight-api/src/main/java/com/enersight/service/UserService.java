package com.enersight.service;

import com.enersight.dto.UserPrincipal;
import com.enersight.dto.UserRegisterRequestDto;
import com.enersight.dto.UserResponseDto;
import com.enersight.dto.UserTermsHistoryDto;
import com.enersight.entity.TermsOfUse;
import com.enersight.entity.DataSubjectRequest;
import com.enersight.entity.User;
import com.enersight.entity.UserTermsAcceptance;
import com.enersight.entity.enums.AcceptanceStatus;
import com.enersight.entity.enums.AuditAction;
import com.enersight.entity.enums.DataSubjectRequestType;
import com.enersight.entity.enums.TermType;
import com.enersight.entity.enums.UserRole;
import com.enersight.entity.enums.UserStatus;
import com.enersight.exception.InvalidUserStateException;
import com.enersight.exception.MandatoryTermsNotAcceptedException;
import com.enersight.exception.ResourceNotFoundException;
import com.enersight.exception.UserAlreadyExistsException;
import com.enersight.repository.DataSubjectRequestRepository;
import com.enersight.repository.TermsOfUseRepository;
import com.enersight.repository.UserRepository;
import com.enersight.repository.UserTermsAcceptanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
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
    private final DataSubjectRequestRepository requestRepository;
    private final AuditService auditService;

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
                .role(UserRole.USER)
                .status(UserStatus.PENDING)
                .build();
        user = userRepository.save(user);
        recordAcceptances(user, dto.getAcceptedTermIds());
        auditService.record(user.getId(), AuditAction.REGISTER, user.getId());
        return toResponse(user);
    }

    public UserResponseDto findById(UserPrincipal currentUser, UUID id) {
        requireSelfOrAdmin(currentUser, id);
        return toResponse(getOrThrow(id));
    }

    public List<UserResponseDto> list(UserPrincipal currentUser, UserStatus status) {
        requireAdmin(currentUser);
        List<User> users = status == null ? userRepository.findAll() : userRepository.findAllByStatus(status);
        return users.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public UserResponseDto approveAccess(UserPrincipal currentUser, UUID id) {
        requireAdmin(currentUser);
        User user = getPendingOrThrow(id);
        user.setStatus(UserStatus.ACTIVE);
        user = userRepository.save(user);
        auditService.record(currentUser.getId(), AuditAction.APPROVE, id);
        return toResponse(user);
    }

    @Transactional
    public UserResponseDto reject(UserPrincipal currentUser, UUID id, String reason) {
        requireAdmin(currentUser);
        User user = getPendingOrThrow(id);
        user.setStatus(UserStatus.REJECTED);
        user.setRejectionReason(reason);
        user = userRepository.save(user);
        auditService.record(currentUser.getId(), AuditAction.REJECT, id);
        return toResponse(user);
    }

    /** Anonymizes instead of deleting: the row stays (acceptance history), personal data is scrubbed. */
    @Transactional
    public void anonymize(UserPrincipal currentUser, UUID targetUserId) {
        requireSelfOrAdmin(currentUser, targetUserId);
        User user = getOrThrow(targetUserId);
        if (user.getStatus() == UserStatus.ANONYMIZED) {
            throw new InvalidUserStateException("User is already anonymized");
        }
        user.setName("Anonymized");
        user.setEmail("anon-" + targetUserId + "@anonymized.invalid");
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setRejectionReason(null);
        user.setActive(false);
        user.setStatus(UserStatus.ANONYMIZED);
        userRepository.save(user);
        if (currentUser.getId().equals(targetUserId)) {
            OffsetDateTime now = OffsetDateTime.now();
            requestRepository.save(DataSubjectRequest.builder()
                    .userId(targetUserId)
                    .type(DataSubjectRequestType.ANONYMIZATION)
                    .requestedAt(now)
                    .completedAt(now)
                    .build());
        }
        auditService.record(currentUser.getId(), AuditAction.ANONYMIZE, targetUserId);
    }

    public List<UserTermsHistoryDto> getTermsHistory(UserPrincipal currentUser, UUID userId) {
        requireSelfOrAdmin(currentUser, userId);
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

    private User getPendingOrThrow(UUID id) {
        User user = getOrThrow(id);
        if (user.getStatus() != UserStatus.PENDING) {
            throw new InvalidUserStateException("User is not pending approval: " + id);
        }
        return user;
    }

    private void requireAdmin(UserPrincipal currentUser) {
        if (!currentUser.hasRole(UserRole.ADMIN)) {
            throw new AccessDeniedException("Admin role required");
        }
    }

    private void requireSelfOrAdmin(UserPrincipal currentUser, UUID targetUserId) {
        if (!currentUser.getId().equals(targetUserId)) {
            requireAdmin(currentUser);
        }
    }

    public UserResponseDto toResponse(User user) {
        return UserResponseDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .status(user.getStatus())
                .rejectionReason(user.getRejectionReason())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
