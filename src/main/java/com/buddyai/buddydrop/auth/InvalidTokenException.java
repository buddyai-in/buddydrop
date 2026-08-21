package com.buddyai.buddydrop.auth;

/** Raised when a magic-link token is unknown, expired, or already consumed. */
public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException() {
        super("Invalid or expired sign-in link");
    }
}
