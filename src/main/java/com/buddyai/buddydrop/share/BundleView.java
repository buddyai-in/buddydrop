package com.buddyai.buddydrop.share;

import com.buddyai.buddydrop.file.StorageUsage;

import java.time.Instant;
import java.util.List;

/** Read-only metadata for the public bundle download page. */
public record BundleView(
        String token,
        List<Item> files,
        String sharedBy,
        Instant expiresAt,
        boolean requiresPassword,
        long totalBytes) {

    public String totalHuman() {
        return StorageUsage.human(totalBytes);
    }

    public int count() {
        return files.size();
    }

    /** One file line on the bundle page. */
    public record Item(String name, String sizeHuman) {
    }
}
