package com.enersight.service;

import com.enersight.dto.AuditLogResponseDto;
import com.enersight.dto.UserPrincipal;
import com.enersight.entity.AuditLog;
import com.enersight.entity.User;
import com.enersight.entity.enums.AuditAction;
import com.enersight.entity.enums.UserRole;
import com.enersight.repository.AuditLogRepository;
import com.enersight.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuditServiceTest {

    private final AuditLogRepository auditRepository = mock(AuditLogRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuditService service = new AuditService(auditRepository, userRepository);

    private final UserPrincipal admin = new UserPrincipal(UUID.randomUUID(), "a@example.test", "hash", UserRole.ADMIN);
    private final UserPrincipal user = new UserPrincipal(UUID.randomUUID(), "u@example.test", "hash", UserRole.USER);

    @Test
    void nonAdminCannotListAuditLog() {
        assertThrows(AccessDeniedException.class, () -> service.list(user));
        verifyNoInteractions(auditRepository);
    }

    @Test
    void listResolvesCurrentActorNameAndRole() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        AuditLog entry = AuditLog.builder().id(UUID.randomUUID()).actorId(actorId).action(AuditAction.ANONYMIZE)
                .targetId(targetId).createdAt(OffsetDateTime.now()).build();
        User actor = User.builder().id(actorId).name("Anonymized").role(UserRole.USER).build();
        when(auditRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(entry));
        when(userRepository.findAllById(List.of(actorId))).thenReturn(List.of(actor));

        List<AuditLogResponseDto> result = service.list(admin);

        assertEquals(1, result.size());
        assertEquals(AuditAction.ANONYMIZE, result.get(0).getAction());
        assertEquals("Anonymized", result.get(0).getActorName());
        assertEquals(UserRole.USER, result.get(0).getActorRole());
        assertEquals(targetId, result.get(0).getTargetId());
    }

    @Test
    void entriesWithoutActorHaveNoName() {
        AuditLog entry = AuditLog.builder().id(UUID.randomUUID()).action(AuditAction.LOGIN_FAILURE)
                .createdAt(OffsetDateTime.now()).build();
        when(auditRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(entry));
        when(userRepository.findAllById(List.of())).thenReturn(List.of());

        AuditLogResponseDto dto = service.list(admin).get(0);

        assertNull(dto.getActorName());
        assertNull(dto.getActorId());
    }
}
