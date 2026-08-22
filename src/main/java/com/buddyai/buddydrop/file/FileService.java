package com.buddyai.buddydrop.file;

import com.buddyai.buddydrop.config.AppProperties;
import com.buddyai.buddydrop.domain.AppUser;
import com.buddyai.buddydrop.domain.FileStatus;
import com.buddyai.buddydrop.domain.StoredFile;
import com.buddyai.buddydrop.domain.UsageKind;
import com.buddyai.buddydrop.exception.NotFoundException;
import com.buddyai.buddydrop.exception.PayloadTooLargeException;
import com.buddyai.buddydrop.exception.QuotaExceededException;
import com.buddyai.buddydrop.file.dto.PresignUploadRequest;
import com.buddyai.buddydrop.file.dto.PresignUploadResponse;
import com.buddyai.buddydrop.repository.AppUserRepository;
import com.buddyai.buddydrop.repository.StoredFileRepository;
import com.buddyai.buddydrop.storage.PresignedUpload;
import com.buddyai.buddydrop.storage.StorageService;
import com.buddyai.buddydrop.usage.UsageLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Owns the file lifecycle: quota-checked presigned uploads, a two-step confirm that trusts the store
 * (not the client) for the final size, downloads via presigned GET, and deletes that clean up S3,
 * shares, and metadata together.
 *
 * <p>Every operation is scoped by owner id taken from the session — the file id in a URL is never
 * trusted on its own, so one user can never reach another's object.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FileService {

    private final StoredFileRepository files;
    private final AppUserRepository users;
    private final StorageService storage;
    private final AppProperties properties;
    private final ShareCleanup shareCleanup;
    private final UsageLimitService usageLimits;

    @Transactional(readOnly = true)
    public List<StoredFile> listFiles(UUID ownerId) {
        return files.findByOwnerIdAndStatusOrderByCreatedAtDesc(ownerId, FileStatus.READY);
    }

    /** One page of a user's READY files, newest first. */
    @Transactional(readOnly = true)
    public Page<StoredFile> listFiles(UUID ownerId, Pageable pageable) {
        return files.findByOwnerIdAndStatusOrderByCreatedAtDesc(ownerId, FileStatus.READY, pageable);
    }

    @Transactional(readOnly = true)
    public StorageUsage usage(UUID ownerId) {
        AppUser user = users.findById(ownerId).orElseThrow(() -> new NotFoundException("User not found"));
        return new StorageUsage(files.sumSizeByOwner(ownerId), user.getQuotaBytes());
    }

    /**
     * Reserve a file id, write a PENDING row, and hand back a presigned PUT. Rejects the request up
     * front if it would breach the per-file limit or the account quota (using the declared size).
     */
    @Transactional
    public PresignUploadResponse initiateUpload(UUID ownerId, PresignUploadRequest request) {
        long maxUpload = properties.getStorage().getMaxUploadBytes();
        if (request.size() > maxUpload) {
            throw new PayloadTooLargeException(
                    "File exceeds the %s per-file limit".formatted(StorageUsage.human(maxUpload)));
        }
        StorageUsage usage = usage(ownerId);
        if (usage.usedBytes() + request.size() > usage.quotaBytes()) {
            throw new QuotaExceededException(
                    "Not enough space — %s of %s used".formatted(
                            StorageUsage.human(usage.usedBytes()), StorageUsage.human(usage.quotaBytes())));
        }

        // Count this upload against the per-user hour/day/month limits (throws 429 if exceeded).
        usageLimits.recordAction(ownerId, UsageKind.UPLOAD);

        UUID fileId = UUID.randomUUID();
        String key = storage.buildKey(ownerId, fileId, request.filename());
        files.save(StoredFile.builder()
                .id(fileId)
                .ownerId(ownerId)
                .s3Key(key)
                .originalName(request.filename())
                .contentType(request.contentType())
                .sizeBytes(request.size())
                .status(FileStatus.PENDING)
                .build());

        PresignedUpload upload = storage.presignUpload(key, request.contentType());
        return new PresignUploadResponse(fileId, upload);
    }

    /**
     * Promote a PENDING upload to READY once the browser reports the PUT finished. The real size is
     * read from the store (the client's declared size is not trusted), and quota is re-checked against
     * it; a file that never actually landed in the store is rejected.
     */
    @Transactional
    public StoredFile confirmUpload(UUID ownerId, UUID fileId) {
        StoredFile file = files.findByIdAndOwnerId(fileId, ownerId)
                .orElseThrow(() -> new NotFoundException("File not found"));

        long actualSize = storage.objectSize(file.getS3Key())
                .orElseThrow(() -> new NotFoundException("Upload was not found in storage"));

        long maxUpload = properties.getStorage().getMaxUploadBytes();
        if (actualSize > maxUpload) {
            storage.delete(file.getS3Key());
            files.delete(file);
            throw new PayloadTooLargeException(
                    "File exceeds the %s per-file limit".formatted(StorageUsage.human(maxUpload)));
        }

        file.setSizeBytes(actualSize);
        file.setStatus(FileStatus.READY);
        log.info("Confirmed upload {} ({} bytes) for owner {}", fileId, actualSize, ownerId);
        return file;
    }

    @Transactional
    public String presignDownload(UUID ownerId, UUID fileId) {
        StoredFile file = requireReady(ownerId, fileId);
        // Count this download against the per-user hour/day/month limits (throws 429 if exceeded).
        usageLimits.recordAction(ownerId, UsageKind.DOWNLOAD);
        return storage.presignDownload(file.getS3Key(), file.getOriginalName());
    }

    @Transactional
    public void delete(UUID ownerId, UUID fileId) {
        StoredFile file = files.findByIdAndOwnerId(fileId, ownerId)
                .orElseThrow(() -> new NotFoundException("File not found"));
        removeFile(file);
        log.info("Deleted file {} for owner {}", fileId, ownerId);
    }

    /**
     * Delete several of the caller's files at once. Only files the caller actually owns are touched;
     * unknown or foreign ids are silently ignored (a bulk action shouldn't fail on a stale id).
     *
     * @return the number of files deleted
     */
    @Transactional
    public int deleteMany(UUID ownerId, Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        List<StoredFile> owned = files.findByOwnerIdAndIdIn(ownerId, ids);
        owned.forEach(this::removeFile);
        log.info("Bulk-deleted {} file(s) for owner {}", owned.size(), ownerId);
        return owned.size();
    }

    private void removeFile(StoredFile file) {
        shareCleanup.removeSharesForFile(file.getId());
        storage.delete(file.getS3Key());
        files.delete(file);
    }

    @Transactional(readOnly = true)
    public StoredFile requireOwnedFile(UUID ownerId, UUID fileId) {
        return files.findByIdAndOwnerId(fileId, ownerId)
                .orElseThrow(() -> new NotFoundException("File not found"));
    }

    private StoredFile requireReady(UUID ownerId, UUID fileId) {
        StoredFile file = files.findByIdAndOwnerId(fileId, ownerId)
                .orElseThrow(() -> new NotFoundException("File not found"));
        if (file.getStatus() != FileStatus.READY) {
            throw new NotFoundException("File is not ready");
        }
        return file;
    }
}
