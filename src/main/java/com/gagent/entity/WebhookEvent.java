package com.gagent.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "webhook_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private String repository;

    private String branch;

    @Column(name = "workflow_name")
    private String workflowName;

    @Column(nullable = false)
    private String status;

    @Column(name = "commit_sha")
    private String commitSha;

    @Column(name = "commit_message", length = 1000)
    private String commitMessage;

    @Column(name = "run_url", length = 1000)
    private String runUrl;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @PrePersist
    protected void onCreate() {
        this.receivedAt = Instant.now();
    }
}
