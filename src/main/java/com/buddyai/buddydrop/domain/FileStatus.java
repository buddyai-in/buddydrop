package com.buddyai.buddydrop.domain;

/**
 * Lifecycle of a stored object. A row is written as {@link #PENDING} when a presigned upload
 * URL is issued, and promoted to {@link #READY} once the browser confirms the S3 PUT succeeded.
 * Rows left PENDING (upload abandoned) can be swept later.
 */
public enum FileStatus {
    PENDING,
    READY
}
