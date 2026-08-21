package com.buddyai.buddydrop.share.dto;

/**
 * Requested share options from the dialog.
 *
 * @param expiresInDays null = never expires
 * @param maxDownloads  null = unlimited downloads
 * @param password      null or blank = no password; any value sets/replaces it
 * @param regenerate    true = mint a fresh token (rotates the link and resets its download count)
 */
public record ShareSettings(
        Integer expiresInDays,
        Integer maxDownloads,
        String password,
        boolean regenerate) {
}
