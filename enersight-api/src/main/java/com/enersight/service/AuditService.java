package com.enersight.service;

import com.enersight.dto.AuditLogResponseDto;
import com.enersight.dto.UserPrincipal;
import com.enersight.entity.AuditLog;
import com.enersight.entity.User;
import com.enersight.entity.enums.AuditAction;
import com.enersight.entity.enums.UserRole;
import com.enersight.repository.AuditLogRepository;
import com.enersight.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;
    private final UserRepository userRepository;

    /** Stores ids only; never put personal data in the audit log. */
    public void record(UUID actorId, AuditAction action, UUID targetId) {
        repository.save(AuditLog.builder().actorId(actorId).action(action).targetId(targetId).build());
    }

    /** Names are resolved at read time, so an anonymized actor shows as anonymized. */
    public List<AuditLogResponseDto> list(UserPrincipal currentUser) {
        if (!currentUser.hasRole(UserRole.ADMIN)) {
            throw new AccessDeniedException("Admin role required");
        }
        List<AuditLog> entries = repository.findAllByOrderByCreatedAtDesc();
        List<UUID> actorIds = entries.stream().map(AuditLog::getActorId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, User> actors = userRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return entries.stream().map(entry -> {
            User actor = actors.get(entry.getActorId());
            return AuditLogResponseDto.builder()
                    .id(entry.getId())
                    .createdAt(entry.getCreatedAt())
                    .action(entry.getAction())
                    .actorId(entry.getActorId())
                    .actorName(actor == null ? null : actor.getName())
                    .actorRole(actor == null ? null : actor.getRole())
                    .targetId(entry.getTargetId())
                    .build();
        }).toList();
    }
}
