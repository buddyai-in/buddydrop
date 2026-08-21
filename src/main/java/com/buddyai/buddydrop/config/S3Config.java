package com.buddyai.buddydrop.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Builds the S3 clients from {@link AppProperties.Storage}.
 *
 * <p>Two beans are exposed: an {@link S3Client} for control-plane calls (delete, head) and an
 * {@link S3Presigner} for issuing the presigned PUT/GET URLs that carry the actual bytes. Credentials
 * come from the {@link DefaultCredentialsProvider} chain (env, profile, IAM role) — never hardcoded.
 * An optional {@code endpoint} + path-style toggle lets the same code target MinIO/LocalStack locally.
 */
@Configuration
public class S3Config {

    private final AppProperties.Storage storage;

    public S3Config(AppProperties properties) {
        this.storage = properties.getStorage();
    }

    @Bean
    public S3Client s3Client() {
        var builder = S3Client.builder()
                .region(Region.of(storage.getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(storage.isPathStyleAccess())
                        .build());
        if (storage.getEndpoint() != null && !storage.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(storage.getEndpoint()));
        }
        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        var builder = S3Presigner.builder()
                .region(Region.of(storage.getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(storage.isPathStyleAccess())
                        .build());
        if (storage.getEndpoint() != null && !storage.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(storage.getEndpoint()));
        }
        return builder.build();
    }
}
