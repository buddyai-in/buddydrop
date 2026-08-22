package com.buddyai.buddydrop.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A single public link that bundles several of an owner's files — the recipient downloads them all as
 * one ZIP. Mirrors {@link ShareLink}'s gating (SHA-256-hashed token, optional BCrypt password, expiry,
 * download cap) but references many files via the {@code share_bundle_file} join.
 */
@Entity
@Table(name = "share_bundle",
        indexes = @Index(name = "ix_share_bundle_hash", columnList = "token_hash"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShareBundle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    /** File ids in this bundle (all owned by {@link #ownerId}). */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "share_bundle_file",
            joinColumns = @JoinColumn(name = "bundle_id"))
    @Column(name = "file_id", nullable = false)
    @Builder.Default
    private Set<UUID> fileIds = new HashSet<>();

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "max_downloads")
    private Integer maxDownloads;

    @Column(name = "download_count", nullable = false)
    private int downloadCount;

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
