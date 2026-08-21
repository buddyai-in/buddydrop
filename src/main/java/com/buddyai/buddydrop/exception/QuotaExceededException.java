package com.buddyai.buddydrop.exception;

/** The upload would push the account over its storage quota. Maps to HTTP 409. */
public class QuotaExceededException extends RuntimeException {
    public QuotaExceededException(String message) {
        super(message);
    }
}
