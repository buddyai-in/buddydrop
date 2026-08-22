package com.buddyai.buddydrop.storage;

import com.buddyai.buddydrop.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * S3-backed {@link StorageService}. Uploads and downloads are handled by presigned URLs so object
 * bytes flow browser&#8596;S3, never through the JVM. The bucket is expected to block public access
 * and enforce server-side encryption; reach is only ever granted through these time-boxed URLs.
 */
@Service
@Slf4j
public class S3StorageService implements StorageService {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;
    private final Duration presignTtl;

    public S3StorageService(S3Client s3, S3Presigner presigner, AppProperties properties) {
        this.s3 = s3;
        this.presigner = presigner;
        this.bucket = properties.getStorage().getBucket();
        this.presignTtl = properties.getStorage().getPresignTtl();
    }

    @Override
    public String buildKey(UUID ownerId, UUID fileId, String filename) {
        return "users/%s/%s/%s".formatted(ownerId, fileId, sanitize(filename));
    }

    @Override
    public PresignedUpload presignUpload(String key, String contentType) {
        PutObjectRequest.Builder put = PutObjectRequest.builder().bucket(bucket).key(key);
        if (contentType != null && !contentType.isBlank()) {
            put.contentType(contentType);
        }
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(presignTtl)
                .putObjectRequest(put.build())
                .build();
        var presigned = presigner.presignPutObject(presignRequest);

        Map<String, String> headers = (contentType == null || contentType.isBlank())
                ? Map.of()
                : Map.of("Content-Type", contentType);
        return new PresignedUpload(
                presigned.url().toString(),
                presigned.httpRequest().method().name(),
                headers,
                Instant.now().plus(presignTtl));
    }

    @Override
    public String presignDownload(String key, String downloadName) {
        GetObjectRequest get = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .responseContentDisposition("attachment; filename=\"%s\"".formatted(sanitize(downloadName)))
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(presignTtl)
                .getObjectRequest(get)
                .build();
        return presigner.presignGetObject(presignRequest).url().toString();
    }

    @Override
    public Optional<Long> objectSize(String key) {
        try {
            HeadObjectResponse head = s3.headObject(HeadObjectRequest.builder()
                    .bucket(bucket).key(key).build());
            return Optional.of(head.contentLength());
        } catch (S3Exception e) {
            // Missing object (NoSuchKeyException) or any access failure — treat as "not present".
            return Optional.empty();
        }
    }

    @Override
    public java.io.InputStream openObject(String key) {
        // ResponseInputStream is an InputStream over the S3 object body; caller closes it.
        return s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public void delete(String key) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (S3Exception e) {
            log.warn("Failed to delete object {} (continuing): {}", key, e.getMessage());
        }
    }

    /** Strip path separators from a client-supplied name so it can't escape its key prefix. */
    private static String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "file";
        }
        String cleaned = name.replaceAll("[\\\\/\\r\\n]", "_").trim();
        return cleaned.isBlank() ? "file" : cleaned;
    }
}
