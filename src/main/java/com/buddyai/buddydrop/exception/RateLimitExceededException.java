package com.buddyai.buddydrop.exception;

/** The user has exceeded an upload or download rate limit for a time window. Maps to HTTP 429. */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
