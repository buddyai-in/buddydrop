package com.buddyai.buddydrop.share.dto;

import java.util.List;
import java.util.UUID;

/**
 * A bulk-share request: apply the same options to every selected file. Each file gets a freshly
 * minted link so its URL can be returned. Foreign/unknown ids are ignored server-side.
 */
public record BulkShareRequest(
        List<UUID> ids,
        Integer expiresInDays,
        Integer maxDownloads,
        String password) {
}
