package com.buddyai.buddydrop.auth.social;

/** A sign-in button: the registration id (used in {@code /oauth2/authorization/{id}}) and its label. */
public record SocialProvider(String id, String label) {
}
