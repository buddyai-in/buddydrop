package com.buddyai.buddydrop.mail;

/** Raised when an email could not be handed off to the configured transport. */
public class EmailDeliveryException extends RuntimeException {
    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
