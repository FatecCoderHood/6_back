package com.enersight.dto;

import com.enersight.entity.enums.TermType;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class TermsOfUseResponseDto {
    private UUID id;
    private String title;
    private String content;
    private TermType type;
    private int version;
    private boolean active;
    private OffsetDateTime createdAt;
}
