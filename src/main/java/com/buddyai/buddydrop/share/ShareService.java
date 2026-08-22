package com.buddyai.buddydrop.share;

import com.buddyai.buddydrop.config.AppProperties;
import com.buddyai.buddydrop.domain.AppUser;
import com.buddyai.buddydrop.domain.FileStatus;
import com.buddyai.buddydrop.domain.ShareLink;
import com.buddyai.buddydrop.domain.StoredFile;
import com.buddyai.buddydrop.domain.UsageKind;
import com.buddyai.buddydrop.exception.InvalidPasswordException;
import com.buddyai.buddydrop.exception.NotFoundException;
import com.buddyai.buddydrop.exception.ShareUnavailableException;
import com.buddyai.buddydrop.file.ShareCleanup;
import com.buddyai.buddydrop.repository.AppUserRepository;
import com.buddyai.buddydrop.repository.ShareLinkRepository;
import com.buddyai.buddydrop.repository.StoredFileRepository;
import com.buddyai.buddydrop.share.dto.BulkShareLink;
import com.buddyai.buddydrop.share.dto.ShareInfo;
import com.buddyai.buddydrop.share.dto.ShareResult;
import com.buddyai.buddydrop.share.dto.ShareSettings;
import com.buddyai.buddydrop.storage.StorageService;
import com.buddyai.buddydrop.usage.UsageLimitService;
import com.buddyai.buddydrop.util.Tokens;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Turns an owned file into a public transfer link and gates anonymous downloads through it.
 *
 * <p>Ownership is checked directly against the file repository (not via {@code FileService}) so the
 * dependency stays one-directional and there is no service cycle — this class <em>implements</em>
 * {@link ShareCleanup}, the seam the file module calls on delete. Tokens are stored only as SHA-256
 * hashes and shown once at creation; passwords are BCrypt-hashed; expiry and download caps are
 * enforced server-side on every public hit.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ShareService implements ShareCleanup {

    private final ShareLinkRepository shares;
    private final StoredFileRepository files;
    private final AppUserRepository users;
    private final StorageService storage;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;
    private final UsageLimitService usageLimits;

    // ---- owner side -------------------------------------------------------

    @Transactional(readOnly = true)
    public ShareInfo getShareInfo(UUID ownerId, UUID fileId) {
        requireOwnedFile(ownerId, fileId);
        return shares.findByFileId(fileId).map(ShareInfo::of).orElse(ShareInfo.none(fileId));
    }

    /** Map of fileId → active share, for rendering dashboard badges without N+1 queries. */
    @Transactional(readOnly = true)
    public Map<UUID, ShareLink> sharesFor(Collection<UUID> fileIds) {
        if (fileIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ShareLink> byFile = new HashMap<>();
        for (ShareLink link : shares.findByFileIdIn(fileIds)) {
            byFile.put(link.getFileId(), link);
        }
        return byFile;
    }

    /**
     * Create a share, or update an existing one's settings. A fresh token (and a returned URL) is
     * minted when none exists yet or {@code regenerate} is set; otherwise only expiry/cap/password
     * change and the URL is not re-derivable.
     */
    @Transactional
    public ShareResult share(UUID ownerId, UUID fileId, ShareSettings settings) {
        requireOwnedFile(ownerId, fileId);
        ShareLink link = shares.findByFileId(fileId).orElse(null);

        String rawToken = null;
        if (link == null) {
            rawToken = Tokens.generate();
            link = ShareLink.builder()
                    .fileId(fileId)
                    .tokenHash(Tokens.hash(rawToken))
                    .downloadCount(0)
                    .build();
        } else if (settings.regenerate()) {
            rawToken = Tokens.generate();
            link.setTokenHash(Tokens.hash(rawToken));
            link.setDownloadCount(0);
        }

        link.setExpiresAt(settings.expiresInDays() == null ? null
                : Instant.now().plus(Duration.ofDays(settings.expiresInDays())));
        link.setMaxDownloads(settings.maxDownloads());
        if (settings.password() != null) {
            link.setPasswordHash(settings.password().isBlank() ? null
                    : passwordEncoder.encode(settings.password()));
        }

        ShareLink saved = shares.save(link);
        String url = rawToken == null ? null : buildShareUrl(rawToken);
        log.info("Shared file {} (regenerated={})", fileId, rawToken != null);
        return new ShareResult(url, ShareInfo.of(saved));
    }

    @Transactional
    public void revoke(UUID ownerId, UUID fileId) {
        requireOwnedFile(ownerId, fileId);
        shares.deleteByFileId(fileId);
        log.info("Revoked share for file {}", fileId);
    }

    /**
     * Share several of the caller's files at once with the same options, minting a fresh link for
     * each so every URL can be returned. Only owned files are touched; unknown ids are ignored.
     */
    @Transactional
    public List<BulkShareLink> shareMany(UUID ownerId, Collection<UUID> ids, ShareSettings settings) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        // Force a fresh token per file so a copyable URL is always produced.
        ShareSettings perFile = new ShareSettings(
                settings.expiresInDays(), settings.maxDownloads(), settings.password(), true);

        List<BulkShareLink> links = new ArrayList<>();
        for (StoredFile file : files.findByOwnerIdAndIdIn(ownerId, ids)) {
            ShareResult result = share(ownerId, file.getId(), perFile);
            links.add(new BulkShareLink(file.getId(), file.getOriginalName(), result.url()));
        }
        log.info("Bulk-shared {} file(s) for owner {}", links.size(), ownerId);
        return links;
    }

    @Override
    @Transactional
    public void removeSharesForFile(UUID fileId) {
        shares.deleteByFileId(fileId);
    }

    // ---- public side ------------------------------------------------------

    /** Metadata for the public download page; throws if the link is unknown or no longer usable. */
    @Transactional(readOnly = true)
    public PublicShareView resolvePublic(String rawToken) {
        ShareLink link = activeLink(rawToken);
        StoredFile file = readyFile(link);
        String sharedBy = users.findById(file.getOwnerId()).map(AppUser::getEmail).orElse("a BuddyDrop user");
        return PublicShareView.of(rawToken, file.getOriginalName(), file.getSizeBytes(),
                sharedBy, link.getExpiresAt(), link.requiresPassword());
    }

    /**
     * Verify access (password if set, still active), count the download, and return a presigned GET.
     * The counter increment and cap check share one transaction.
     */
    @Transactional
    public String resolveDownload(String rawToken, String password) {
        ShareLink link = activeLink(rawToken);
        if (link.requiresPassword()) {
            if (password == null || !passwordEncoder.matches(password, link.getPasswordHash())) {
                throw new InvalidPasswordException("Incorrect password");
            }
        }
        StoredFile file = readyFile(link);
        // Public downloads count against the file owner's per-hour/day/month limits (throws 429 if over).
        usageLimits.recordAction(file.getOwnerId(), UsageKind.DOWNLOAD);
        link.setDownloadCount(link.getDownloadCount() + 1);
        log.info("Public download {} of file {} (count={})", rawToken.substring(0, 6), file.getId(),
                link.getDownloadCount());
        return storage.presignDownload(file.getS3Key(), file.getOriginalName());
    }

    // ---- helpers ----------------------------------------------------------

    private ShareLink activeLink(String rawToken) {
        ShareLink link = shares.findByTokenHash(Tokens.hash(rawToken))
                .orElseThrow(() -> new NotFoundException("This link is not valid"));
        if (!link.isActive(Instant.now())) {
            throw new ShareUnavailableException(link.isExhausted()
                    ? "This link has reached its download limit"
                    : "This link has expired");
        }
        return link;
    }

    private StoredFile readyFile(ShareLink link) {
        StoredFile file = files.findById(link.getFileId())
                .orElseThrow(() -> new NotFoundException("The shared file no longer exists"));
        if (file.getStatus() != FileStatus.READY) {
            throw new NotFoundException("The shared file is not available");
        }
        return file;
    }

    private StoredFile requireOwnedFile(UUID ownerId, UUID fileId) {
        return files.findByIdAndOwnerId(fileId, ownerId)
                .orElseThrow(() -> new NotFoundException("File not found"));
    }

    private String buildShareUrl(String rawToken) {
        return UriComponentsBuilder.fromHttpUrl(properties.getBaseUrl())
                .path("/s/").path(rawToken).build().toUriString();
    }
}
