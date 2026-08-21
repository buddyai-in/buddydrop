package com.buddyai.buddydrop.exception;

/** A share link exists but can no longer be used (expired or download cap reached). */
public class ShareUnavailableException extends RuntimeException {
    public ShareUnavailableException(String message) {
        super(message);
    }
}
