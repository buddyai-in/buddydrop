package com.buddyai.buddydrop.support;

import com.buddyai.buddydrop.storage.PresignedUpload;
import com.buddyai.buddydrop.storage.StorageService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link StorageService} for tests. Presigning a PUT records the key and simulates that the
 * object was stored at {@link #simulatedSize} bytes, so the presign &rarr; confirm handshake can be
 * exercised end-to-end through MockMvc without a real (or emulated) S3.
 */
@Service
@Primary
public class FakeStorageService implements StorageService {

    private final Map<String, Long> objects = new ConcurrentHashMap<>();
    private volatile long simulatedSize = 1_024;

    public void setSimulatedSize(long size) {
        this.simulatedSize = size;
    }

    @Override
    public String buildKey(UUID ownerId, UUID fileId, String filename) {
        return "users/%s/%s/%s".formatted(ownerId, fileId, filename);
    }

    @Override
    public PresignedUpload presignUpload(String key, String contentType) {
        objects.put(key, simulatedSize);
        return new PresignedUpload("https://fake-storage.test/" + key, "PUT", Map.of(),
                Instant.now().plusSeconds(600));
    }

    @Override
    public String presignDownload(String key, String downloadName) {
        return "https://fake-storage.test/download/" + key;
    }

    @Override
    public Optional<Long> objectSize(String key) {
        return Optional.ofNullable(objects.get(key));
    }

    @Override
    public void delete(String key) {
        objects.remove(key);
    }
}
