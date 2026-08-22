package com.buddyai.buddydrop.share.dto;

/** Result of a bulk share: a single bundle link that downloads all selected files as one ZIP. */
public record BundleShareResult(String url, int fileCount) {
}
