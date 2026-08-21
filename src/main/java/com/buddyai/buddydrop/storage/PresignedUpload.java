package com.buddyai.buddydrop.storage;

import java.time.Instant;
import java.util.Map;

/**
 * A presigned upload instruction handed to the browser. The client issues an HTTP {@code PUT} to
 * {@link #url} with the given {@link #headers} and the file bytes as the body — the bytes never
 * pass through the application.
 */
public record PresignedUpload(String url, String method, Map<String, String> headers, Instant expiresAt) {
}
