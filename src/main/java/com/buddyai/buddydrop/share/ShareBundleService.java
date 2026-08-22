package com.buddyai.buddydrop.share;

import com.buddyai.buddydrop.config.AppProperties;
import com.buddyai.buddydrop.domain.*;
import com.buddyai.buddydrop.exception.InvalidPasswordException;
import com.buddyai.buddydrop.exception.NotFoundException;
import com.buddyai.buddydrop.exception.ShareUnavailableException;
import com.buddyai.buddydrop.repository.AppUserRepository;
import com.buddyai.buddydrop.repository.ShareBundleRepository;
import com.buddyai.buddydrop.repository.StoredFileRepository;
import com.buddyai.buddydrop.share.dto.ShareSettings;
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
 * A multi-file share: one public link (<code>/d/{token}</code>) whose recipient downloads every file
 * as a single ZIP. Mirrors {@link ShareService}'s gating (hashed token, optional BCrypt password,
 * expiry, download cap) but over a set of files. Ownership is checked against the file repository, so
 * this stays a leaf service with no cycle back into the file module.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ShareBundleService {

    private final ShareBundleRepository bundles;
    private final StoredFileRepository files;
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;
    private final UsageLimitService usageLimits;

    // ---- owner side -------------------------------------------------------

    /**
     * Create a bundle over the caller's selected files and return its single public URL. Only owned,
     * READY files are included; a fresh token is always minted.
     *
     * @throws NotFoundException if none of the ids resolve to an owned, ready file
     */
    @Transactional
    public String create(UUID ownerId, Collection<UUID> ids, ShareSettings settings) {
        if (ids == null || ids.isEmpty()) {
            throw new NotFoundException("No files selected");
        }
        Set<UUID> owned = new LinkedHashSet<>();
        for (StoredFile f : files.findByOwnerIdAndIdIn(ownerId, ids)) {
            if (f.getStatus() == FileStatus.READY) {
                owned.add(f.getId());
            }
        }
        if (owned.isEmpty()) {
            throw new NotFoundException("No shareable files selected");
        }

        String raw = Tokens.generate();
        ShareBundle bundle = ShareBundle.builder()
                .ownerId(ownerId)
                .tokenHash(Tokens.hash(raw))
                .fileIds(owned)
                .downloadCount(0)
                .expiresAt(settings.expiresInDays() == null ? null
                        : Instant.now().plus(Duration.ofDays(settings.expiresInDays())))
                .maxDownloads(settings.maxDownloads())
                .passwordHash(settings.password() == null || settings.password().isBlank() ? null
                        : passwordEncoder.encode(settings.password()))
                .build();
        bundles.save(bundle);
        log.info("Created bundle of {} file(s) for owner {}", owned.size(), ownerId);
        return UriComponentsBuilder.fromHttpUrl(properties.getBaseUrl())
                .path("/d/").path(raw).build().toUriString();
    }

    // ---- public side ------------------------------------------------------

    /** Metadata for the public bundle page. Throws if the link is unknown or no longer usable. */
    @Transactional(readOnly = true)
    public BundleView resolvePublic(String rawToken) {
        ShareBundle bundle = activeBundle(rawToken);
        List<StoredFile> ready = readyFiles(bundle);
        long total = ready.stream().mapToLong(StoredFile::getSizeBytes).sum();
        List<BundleView.Item> items = ready.stream()
                .map(f -> new BundleView.Item(f.getOriginalName(),
                        com.buddyai.buddydrop.file.StorageUsage.human(f.getSizeBytes())))
                .toList();
        String sharedBy = users.findById(bundle.getOwnerId()).map(AppUser::getEmail).orElse("a BuddyAi Drop-In user");
        return new BundleView(rawToken, items, sharedBy, bundle.getExpiresAt(), bundle.requiresPassword(), total);
    }

    /** Verify a bundle password (used to unlock the download). */
    @Transactional(readOnly = true)
    public void verifyPassword(String rawToken, String password) {
        ShareBundle bundle = activeBundle(rawToken);
        if (bundle.requiresPassword()
                && (password == null || !passwordEncoder.matches(password, bundle.getPasswordHash()))) {
            throw new InvalidPasswordException("Incorrect password");
        }
    }

    /**
     * Authorize and account a bundle download, returning the files to stream into the ZIP. Password is
     * assumed already verified by the caller (session unlock). Enforces the download cap and records a
     * download against the owner's rate limits (one per file); all-or-nothing in one transaction.
     */
    @Transactional
    public List<StoredFile> prepareDownload(String rawToken) {
        ShareBundle bundle = activeBundle(rawToken);
        List<StoredFile> ready = readyFiles(bundle);
        if (ready.isEmpty()) {
            throw new NotFoundException("The shared files are no longer available");
        }
        ready.forEach(f -> usageLimits.recordAction(bundle.getOwnerId(), UsageKind.DOWNLOAD));
        bundle.setDownloadCount(bundle.getDownloadCount() + 1);
        log.info("Bundle download {} ({} files, count={})", rawToken.substring(0, 6), ready.size(),
                bundle.getDownloadCount());
        return ready;
    }

    // ---- helpers ----------------------------------------------------------

    private ShareBundle activeBundle(String rawToken) {
        ShareBundle bundle = bundles.findByTokenHash(Tokens.hash(rawToken))
                .orElseThrow(() -> new NotFoundException("This link is not valid"));
        if (!bundle.isActive(Instant.now())) {
            throw new ShareUnavailableException(bundle.isExhausted()
                    ? "This link has reached its download limit"
                    : "This link has expired");
        }
        return bundle;
    }

    private List<StoredFile> readyFiles(ShareBundle bundle) {
        return files.findAllById(bundle.getFileIds()).stream()
                .filter(f -> f.getStatus() == FileStatus.READY)
                .toList();
    }
}
