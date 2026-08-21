package com.buddyai.buddydrop.file.dto;

import com.buddyai.buddydrop.domain.StoredFile;
import com.buddyai.buddydrop.file.StorageUsage;

import java.time.Instant;
import java.util.UUID;

/** Serializable view of a stored file returned to the dashboard's JavaScript. */
public record FileResponse(
        UUID id,
        String name,
        long size,
        String sizeHuman,
        String contentType,
        Instant createdAt) {

    public static FileResponse from(StoredFile f) {
        return new FileResponse(
                f.getId(),
                f.getOriginalName(),
                f.getSizeBytes(),
                StorageUsage.human(f.getSizeBytes()),
                f.getContentType(),
                f.getCreatedAt());
    }
}
