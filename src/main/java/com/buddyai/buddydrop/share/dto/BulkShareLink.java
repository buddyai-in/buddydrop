package com.buddyai.buddydrop.share.dto;

import java.util.UUID;

/** One file's freshly minted share link, returned from a bulk-share so the UI can list the URLs. */
public record BulkShareLink(UUID fileId, String name, String url) {
}
