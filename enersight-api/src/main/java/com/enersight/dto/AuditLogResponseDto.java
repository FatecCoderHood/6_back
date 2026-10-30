package com.enersight.dto;

import com.enersight.entity.enums.AuditAction;
import com.enersight.entity.enums.UserRole;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class AuditLogResponseDto {
    private UUID id;
    private OffsetDateTime createdAt;
    private AuditAction action;
    private UUID actorId;
    private String actorName;
    private UserRole actorRole;
    private UUID targetId;
}
