package com.buddyai.buddydrop.share;

import com.buddyai.buddydrop.file.StorageUsage;

import java.time.Instant;

/** Read-only metadata rendered on the public {@code /s/{token}} download page. */
public record PublicShareView(
        String token,
        String filename,
        long sizeBytes,
        String sizeHuman,
        String sharedBy,
        Instant expiresAt,
        boolean requiresPassword) {

    public static PublicShareView of(String token, String filename, long size,
                                     String sharedBy, Instant expiresAt, boolean requiresPassword) {
        return new PublicShareView(token, filename, size, StorageUsage.human(size),
                sharedBy, expiresAt, requiresPassword);
    }
}
