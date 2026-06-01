package com.enersight.dto;

import com.enersight.entity.enums.UserRole;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class UserResponseDto {
    private UUID id;
    private String name;
    private String email;
    private UserRole role;
    private boolean active;
    private OffsetDateTime createdAt;
}
