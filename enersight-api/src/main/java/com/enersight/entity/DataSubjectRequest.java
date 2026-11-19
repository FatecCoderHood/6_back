package com.enersight.entity;

import com.enersight.entity.enums.DataSubjectRequestType;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "data_subject_requests", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSubjectRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private DataSubjectRequestType type;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
}
