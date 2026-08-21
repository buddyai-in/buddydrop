package com.buddyai.buddydrop.auth;

/** Raised when magic-link requests for an address exceed the configured throttle. */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException() {
        super("Too many sign-in requests. Please wait a few minutes and try again.");
    }
}
