package com.buddyai.buddydrop.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.time.Duration;

/**
 * Strongly-typed application configuration, bound from the {@code buddydrop.*} tree.
 *
 * <p>Grouping every tunable here (rather than scattering {@code @Value} lookups) keeps the
 * configuration surface discoverable and lets services depend on a single, validated bean.
 */
@Data
@ConfigurationProperties(prefix = "buddydrop")
public class AppProperties {

    /** Absolute base URL used to build links in outgoing emails, e.g. {@code https://buddydrop.app}. */
    private String baseUrl = "http://localhost:8080";

    @NestedConfigurationProperty
    private Auth auth = new Auth();

    @NestedConfigurationProperty
    private Storage storage = new Storage();

    @NestedConfigurationProperty
    private Mail mail = new Mail();

    @Data
    public static class Auth {
        /** How long a magic-link token remains valid after issue. */
        private Duration tokenTtl = Duration.ofMinutes(15);
        /** How long an authenticated session stays alive (server session timeout). */
        private Duration sessionTtl = Duration.ofMinutes(60);
        /** Max magic-link requests allowed per email within the throttle window. */
        private int maxRequestsPerWindow = 5;
        /** Sliding window for the per-email request throttle. */
        private Duration requestWindow = Duration.ofMinutes(15);
    }

    @Data
    public static class Storage {
        /** Target S3 bucket for stored objects. */
        private String bucket = "buddydrop-dev";
        /** AWS region of the bucket. */
        private String region = "us-east-1";
        /** Optional S3-compatible endpoint override (e.g. MinIO/LocalStack for local dev). */
        private String endpoint;
        /** Force path-style addressing — required by most S3-compatible local emulators. */
        private boolean pathStyleAccess = false;
        /** Validity window for issued presigned URLs. */
        private Duration presignTtl = Duration.ofMinutes(10);
        /** Per-account storage quota in bytes (default 10 GB). */
        private long quotaBytes = 10L * 1024 * 1024 * 1024;
        /** Largest single upload accepted, in bytes (default 5 GB). */
        private long maxUploadBytes = 5L * 1024 * 1024 * 1024;
    }

    @Data
    public static class Mail {
        /** From address on magic-link emails. */
        private String from = "no-reply@buddydrop.app";
        /** Display name on magic-link emails. */
        private String fromName = "BuddyDrop";
    }
}
