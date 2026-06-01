package com.enersight.dto;

import com.enersight.entity.enums.AcceptanceStatus;
import com.enersight.entity.enums.TermType;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class UserTermsHistoryDto {
    private UUID termId;
    private String termTitle;
    private TermType termType;
    private AcceptanceStatus status;
    private OffsetDateTime createdAt;
}
