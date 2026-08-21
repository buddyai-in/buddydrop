package com.buddyai.buddydrop.share.dto;

import com.buddyai.buddydrop.domain.ShareLink;

import java.time.Instant;
import java.util.UUID;

/**
 * Owner-facing state of a file's share link — never includes the raw token (which is shown only once,
 * at creation). Drives the share dialog and the dashboard's shared/private badge.
 */
public record ShareInfo(
        boolean exists,
        UUID fileId,
        Instant expiresAt,
        Integer maxDownloads,
        int downloadCount,
        boolean requiresPassword) {

    public static ShareInfo none(UUID fileId) {
        return new ShareInfo(false, fileId, null, null, 0, false);
    }

    public static ShareInfo of(ShareLink link) {
        return new ShareInfo(true, link.getFileId(), link.getExpiresAt(),
                link.getMaxDownloads(), link.getDownloadCount(), link.requiresPassword());
    }
}
