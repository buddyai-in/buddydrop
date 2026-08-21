package com.buddyai.buddydrop.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata for one uploaded object. The bytes live in S3 under {@link #s3Key}; this row is the
 * source of truth for ownership, display name, size, and status.
 */
@Entity
@Table(name = "stored_file", indexes = @Index(name = "ix_stored_file_owner", columnList = "owner_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoredFile {

    // Application-assigned: the id is needed up front to build the S3 key, so no generator here.
    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    /** Full S3 object key, e.g. {@code users/{ownerId}/{fileId}/{name}}. */
    @Column(name = "s3_key", nullable = false, unique = true, length = 1024)
    private String s3Key;

    @Column(name = "original_name", nullable = false, length = 1024)
    private String originalName;

    @Column(name = "content_type", length = 255)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FileStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
