package com.enersight.service;

import com.enersight.dto.ChangePasswordRequestDto;
import com.enersight.dto.UserPrincipal;
import com.enersight.dto.UserRegisterRequestDto;
import com.enersight.dto.UserResponseDto;
import com.enersight.entity.DataSubjectRequest;
import com.enersight.entity.TermsOfUse;
import com.enersight.entity.User;
import com.enersight.entity.UserTermsAcceptance;
import com.enersight.entity.enums.AuditAction;
import com.enersight.entity.enums.DataSubjectRequestType;
import com.enersight.entity.enums.TermType;
import com.enersight.entity.enums.UserRole;
import com.enersight.entity.enums.UserStatus;
import com.enersight.exception.InvalidPasswordChangeException;
import com.enersight.exception.InvalidUserStateException;
import com.enersight.repository.DataSubjectRequestRepository;
import com.enersight.repository.TermsOfUseRepository;
import com.enersight.repository.UserRepository;
import com.enersight.repository.UserTermsAcceptanceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock TermsOfUseRepository termsRepository;
    @Mock UserTermsAcceptanceRepository acceptanceRepository;
    @Mock DataSubjectRequestRepository requestRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuditService auditService;
    @InjectMocks UserService service;

    private final UUID adminId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UserPrincipal admin = new UserPrincipal(adminId, "admin@example.test", "hash", UserRole.ADMIN);
    private final UserPrincipal regular = new UserPrincipal(userId, "user@example.test", "hash", UserRole.USER);

    private User userWithStatus(UUID id, UserStatus status) {
        return User.builder().id(id).name("Name").email("n@example.test").password("hash")
                .role(UserRole.USER).status(status).build();
    }

    // register

    @Test
    void registerCreatesPendingUserRecordsAcceptancesAndAudits() {
        UUID termId = UUID.randomUUID();
        TermsOfUse term = TermsOfUse.builder().id(termId).type(TermType.MANDATORY).build();
        UserRegisterRequestDto dto = new UserRegisterRequestDto();
        dto.setName("Name");
        dto.setEmail("n@example.test");
        dto.setPassword("password-1");
        dto.setAcceptedTermIds(List.of(termId));
        when(userRepository.existsByEmail("n@example.test")).thenReturn(false);
        when(termsRepository.findAllByActiveTrueAndType(TermType.MANDATORY)).thenReturn(List.of(term));
        when(termsRepository.findById(termId)).thenReturn(Optional.of(term));
        when(passwordEncoder.encode("password-1")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            saved.setId(userId);
            return saved;
        });

        UserResponseDto result = service.register(dto);

        assertThat(result.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(result.getRole()).isEqualTo(UserRole.USER);
        verify(acceptanceRepository).save(any(UserTermsAcceptance.class));
        verify(auditService).record(userId, AuditAction.REGISTER, userId);
    }

    // change own password

    private ChangePasswordRequestDto passwordChange(String current, String next) {
        ChangePasswordRequestDto dto = new ChangePasswordRequestDto();
        dto.setCurrentPassword(current);
        dto.setNewPassword(next);
        return dto;
    }

    @Test
    void changePasswordStoresTheNewHashWhenCurrentPasswordMatches() {
        User user = userWithStatus(userId, UserStatus.ACTIVE);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-password", "hash")).thenReturn(true);
        when(passwordEncoder.encode("new-password-1")).thenReturn("new-hash");

        service.changePassword(regular, passwordChange("old-password", "new-password-1"));

        assertThat(user.getPassword()).isEqualTo("new-hash");
        verify(userRepository).save(user);
    }

    @Test
    void changePasswordRejectsAWrongCurrentPassword() {
        User user = userWithStatus(userId, UserStatus.ACTIVE);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.changePassword(regular, passwordChange("wrong", "new-password-1")))
                .isInstanceOf(InvalidPasswordChangeException.class);

        assertThat(user.getPassword()).isEqualTo("hash");
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePasswordRejectsANewPasswordEqualToTheCurrentOne() {
        assertThatThrownBy(() -> service.changePassword(regular, passwordChange("same-password", "same-password")))
                .isInstanceOf(InvalidPasswordChangeException.class);

        verify(userRepository, never()).save(any());
    }

    // approve

    @Test
    void approveByNonAdminIsDenied() {
        assertThatThrownBy(() -> service.approveAccess(regular, userId))
                .isInstanceOf(AccessDeniedException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void approveByAdminActivatesPendingUserAndAudits() {
        User pending = userWithStatus(userId, UserStatus.PENDING);
        when(userRepository.findById(userId)).thenReturn(Optional.of(pending));
        when(userRepository.save(pending)).thenReturn(pending);

        UserResponseDto result = service.approveAccess(admin, userId);

        assertThat(result.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(auditService).record(adminId, AuditAction.APPROVE, userId);
    }

    @Test
    void approveOfNonPendingUserIsRejected() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithStatus(userId, UserStatus.ACTIVE)));

        assertThatThrownBy(() -> service.approveAccess(admin, userId))
                .isInstanceOf(InvalidUserStateException.class);
    }

    // reject

    @Test
    void rejectByNonAdminIsDenied() {
        assertThatThrownBy(() -> service.reject(regular, userId, "reason"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectByAdminStoresReasonAndAudits() {
        User pending = userWithStatus(userId, UserStatus.PENDING);
        when(userRepository.findById(userId)).thenReturn(Optional.of(pending));
        when(userRepository.save(pending)).thenReturn(pending);

        UserResponseDto result = service.reject(admin, userId, "Incomplete data");

        assertThat(result.getStatus()).isEqualTo(UserStatus.REJECTED);
        assertThat(result.getRejectionReason()).isEqualTo("Incomplete data");
        verify(auditService).record(adminId, AuditAction.REJECT, userId);
    }

    @Test
    void rejectOfNonPendingUserIsRejected() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithStatus(userId, UserStatus.ACTIVE)));

        assertThatThrownBy(() -> service.reject(admin, userId, "reason"))
                .isInstanceOf(InvalidUserStateException.class);
    }

    // list

    @Test
    void listByNonAdminIsDenied() {
        assertThatThrownBy(() -> service.list(regular, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void listByAdminReturnsAllUsers() {
        when(userRepository.findAll()).thenReturn(List.of(
                userWithStatus(UUID.randomUUID(), UserStatus.ACTIVE),
                userWithStatus(UUID.randomUUID(), UserStatus.PENDING)));

        assertThat(service.list(admin, null)).hasSize(2);
    }

    @Test
    void listByAdminCanFilterByStatus() {
        when(userRepository.findAllByStatus(UserStatus.PENDING))
                .thenReturn(List.of(userWithStatus(UUID.randomUUID(), UserStatus.PENDING)));

        assertThat(service.list(admin, UserStatus.PENDING))
                .extracting(UserResponseDto::getStatus).containsExactly(UserStatus.PENDING);
    }

    // read access

    @Test
    void findByIdOfAnotherUserIsDeniedForNonAdmin() {
        assertThatThrownBy(() -> service.findById(regular, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void findByIdOfSelfIsAllowed() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithStatus(userId, UserStatus.ACTIVE)));

        assertThat(service.findById(regular, userId).getId()).isEqualTo(userId);
    }

    @Test
    void findByIdOfAnyUserIsAllowedForAdmin() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithStatus(userId, UserStatus.ACTIVE)));

        assertThat(service.findById(admin, userId).getId()).isEqualTo(userId);
    }

    @Test
    void termsHistoryOfAnotherUserIsDeniedForNonAdmin() {
        assertThatThrownBy(() -> service.getTermsHistory(regular, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
    }

    // anonymize

    @Test
    void selfAnonymizeScrubsPersonalDataKeepsAcceptancesAndRecordsRequest() {
        User active = userWithStatus(userId, UserStatus.ACTIVE);
        when(userRepository.findById(userId)).thenReturn(Optional.of(active));
        when(passwordEncoder.encode(any())).thenReturn("unusable-hash");

        service.anonymize(regular, userId);

        assertThat(active.getStatus()).isEqualTo(UserStatus.ANONYMIZED);
        assertThat(active.isActive()).isFalse();
        assertThat(active.getName()).isEqualTo("Anonymized");
        assertThat(active.getEmail()).isEqualTo("anon-" + userId + "@anonymized.invalid");
        assertThat(active.getPassword()).isEqualTo("unusable-hash");
        verify(acceptanceRepository, never()).deleteAll();
        verify(userRepository, never()).delete(any());
        ArgumentCaptor<DataSubjectRequest> request = ArgumentCaptor.forClass(DataSubjectRequest.class);
        verify(requestRepository).save(request.capture());
        assertThat(request.getValue().getUserId()).isEqualTo(userId);
        assertThat(request.getValue().getType()).isEqualTo(DataSubjectRequestType.ANONYMIZATION);
        verify(auditService).record(userId, AuditAction.ANONYMIZE, userId);
    }

    @Test
    void adminAnonymizingAnotherUserAuditsButRecordsNoDataSubjectRequest() {
        User target = userWithStatus(userId, UserStatus.ACTIVE);
        when(userRepository.findById(userId)).thenReturn(Optional.of(target));
        when(passwordEncoder.encode(any())).thenReturn("unusable-hash");

        service.anonymize(admin, userId);

        assertThat(target.getStatus()).isEqualTo(UserStatus.ANONYMIZED);
        verify(requestRepository, never()).save(any());
        verify(auditService).record(adminId, AuditAction.ANONYMIZE, userId);
    }

    @Test
    void anonymizeOfAnotherUserIsDeniedForNonAdmin() {
        assertThatThrownBy(() -> service.anonymize(regular, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void anonymizeOfAlreadyAnonymizedUserIsRejected() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithStatus(userId, UserStatus.ANONYMIZED)));

        assertThatThrownBy(() -> service.anonymize(regular, userId))
                .isInstanceOf(InvalidUserStateException.class);
    }
}
