package com.buddyai.buddydrop.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One rate-limited action (an upload or a download) attributed to a user, timestamped for
 * window counting. Public share downloads are recorded against the file's owner. Rows are counted
 * within rolling windows to enforce per-hour/day/month limits and are purged once past the widest
 * window (see {@code UsageCleanupJob}).
 */
@Entity
@Table(name = "usage_event",
        indexes = @Index(name = "ix_usage_event_lookup", columnList = "user_id, kind, created_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UsageKind kind;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
