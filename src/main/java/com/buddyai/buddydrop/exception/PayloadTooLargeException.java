package com.buddyai.buddydrop.exception;

/** The declared or actual upload size exceeds the per-file limit. Maps to HTTP 413. */
public class PayloadTooLargeException extends RuntimeException {
    public PayloadTooLargeException(String message) {
        super(message);
    }
}
