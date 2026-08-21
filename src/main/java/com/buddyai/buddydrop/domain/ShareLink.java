package com.buddyai.buddydrop.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A public transfer link for a single {@link StoredFile}. The raw token is stored only as a
 * SHA-256 hash; an optional BCrypt-hashed password, expiry, and download cap gate access.
 * One active link per file (unique {@code file_id}) — re-sharing updates the same row.
 */
@Entity
@Table(name = "share_link",
        uniqueConstraints = @UniqueConstraint(name = "uq_share_link_file", columnNames = "file_id"),
        indexes = @Index(name = "ix_share_link_hash", columnList = "token_hash"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShareLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    /** SHA-256 hash (hex) of the raw share token that appears in {@code /s/{token}}. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    /** Null = never expires. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /** Null = unlimited downloads. */
    @Column(name = "max_downloads")
    private Integer maxDownloads;

    @Column(name = "download_count", nullable = false)
    private int downloadCount;

    /** Null = no password required. BCrypt hash otherwise. */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean requiresPassword() {
        return passwordHash != null;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }

    public boolean isExhausted() {
        return maxDownloads != null && downloadCount >= maxDownloads;
    }

    public boolean isActive(Instant now) {
        return !isExpired(now) && !isExhausted();
    }
}
