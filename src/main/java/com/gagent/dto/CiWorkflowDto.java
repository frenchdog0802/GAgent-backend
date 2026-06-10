package com.gagent.dto;

import com.gagent.entity.CiWorkflowStatus;
import lombok.*;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CiWorkflowDto {
    private Long id;
    private Long webhookEventId;
    private String repository;
    private CiWorkflowStatus status;
    private String prUrl;
    private String fixBranch;
    private String lastError;
    private Integer fixAttempt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
