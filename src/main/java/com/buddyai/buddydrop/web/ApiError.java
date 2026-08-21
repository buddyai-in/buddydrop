package com.buddyai.buddydrop.web;

import java.time.Instant;

/** Uniform JSON error body returned by the {@code /api/**} surface. */
public record ApiError(int status, String error, String message, Instant timestamp) {
    public static ApiError of(int status, String error, String message) {
        return new ApiError(status, error, message, Instant.now());
    }
}
