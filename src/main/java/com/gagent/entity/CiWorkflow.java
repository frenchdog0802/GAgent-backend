package com.gagent.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "ci_workflows")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CiWorkflow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "webhook_event_id")
    private Long webhookEventId;

    @Column(nullable = false, length = 500)
    private String repository;

    private String branch;

    @Column(name = "clone_url", nullable = false, length = 1000)
    private String cloneUrl;

    @Column(name = "failing_sha", nullable = false, length = 64)
    private String failingSha;

    @Column(name = "failing_sha_full", length = 64)
    private String failingShaFull;

    @Column(name = "github_run_id")
    private Long githubRunId;

    @Column(name = "sandbox_path", length = 1000)
    private String sandboxPath;

    @Column(name = "test_command")
    private String testCommand;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CiWorkflowStatus status;

    @Column(name = "failure_log", columnDefinition = "TEXT")
    private String failureLog;

    @Column(name = "pr_url", length = 1000)
    private String prUrl;

    @Column(name = "fix_branch")
    private String fixBranch;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "fix_attempt")
    private Integer fixAttempt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = CiWorkflowStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
