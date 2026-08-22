package com.buddyai.buddydrop.storage;

import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

/**
 * Object-storage seam. The application depends on this abstraction, not on S3 directly, so the
 * backend can be swapped (S3, an S3-compatible emulator, or a test fake) without touching business
 * logic. All byte transfer happens client-to-store via presigned URLs; the app only issues them and
 * performs control-plane operations (delete, head).
 */
public interface StorageService {

    /** Deterministic object key for an owned file: {@code users/{ownerId}/{fileId}/{filename}}. */
    String buildKey(UUID ownerId, UUID fileId, String filename);

    /** Issue a short-lived presigned PUT the browser uses to upload directly to the store. */
    PresignedUpload presignUpload(String key, String contentType);

    /**
     * Issue a short-lived presigned GET. {@code downloadName} sets the Content-Disposition filename
     * so the browser saves it under the original name regardless of the opaque key.
     */
    String presignDownload(String key, String downloadName);

    /** Actual stored object size in bytes, or empty if the object is absent. Used to confirm uploads. */
    Optional<Long> objectSize(String key);

    /**
     * Open the object's bytes as a stream. Unlike downloads (which use presigned URLs), this is used
     * to build a ZIP of several files server-side — the one case where bytes flow through the app.
     * The caller must close the stream.
     */
    InputStream openObject(String key);

    /** Permanently remove the object. No-op if it does not exist. */
    void delete(String key);
}
