package com.gagent.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "activity_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false, length = 1000)
    private String command; // E.g., user's request context or action context

    @Column(nullable = false)
    private String action; // E.g., "Send Email"

    @Column(nullable = false)
    private String tool; // E.g., "mail"

    @Column(nullable = false)
    private String status; // E.g., "success", "failed"

    @Column(length = 1000)
    private String error; // Error message if any

    @Column
    private String link; // Optional link to resource
}
