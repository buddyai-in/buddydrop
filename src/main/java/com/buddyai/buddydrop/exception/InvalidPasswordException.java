package com.buddyai.buddydrop.exception;

/** The password supplied for a password-protected share was missing or wrong. */
public class InvalidPasswordException extends RuntimeException {
    public InvalidPasswordException(String message) {
        super(message);
    }
}
