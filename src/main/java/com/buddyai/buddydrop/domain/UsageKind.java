package com.buddyai.buddydrop.domain;

/** The rate-limited operations counted per user across rolling hour/day/month windows. */
public enum UsageKind {
    UPLOAD,
    DOWNLOAD
}
