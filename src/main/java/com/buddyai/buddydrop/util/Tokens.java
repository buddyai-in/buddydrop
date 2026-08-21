package com.buddyai.buddydrop.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Central helpers for opaque security tokens (magic-link and share tokens).
 *
 * <p>Tokens are high-entropy URL-safe strings handed to the client exactly once; only their
 * SHA-256 hash is ever persisted, so the raw value cannot be recovered from the database. A single
 * definition here keeps generation and hashing identical across auth and sharing.
 */
public final class Tokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private Tokens() {
    }

    /** A 256-bit URL-safe random token (~43 chars). */
    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    /** Lower-case hex SHA-256 of the raw token, for storage and lookup. */
    public static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
