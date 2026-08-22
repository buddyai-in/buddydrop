package com.buddyai.buddydrop.storage;

import com.buddyai.buddydrop.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CORSConfiguration;
import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.s3.model.PutBucketCorsRequest;

import java.net.URI;

/**
 * Applies the bucket CORS rule needed for direct browser uploads.
 *
 * <p>A browser {@code PUT} to a presigned S3 URL always triggers a CORS preflight (PUT is never a
 * "simple" request), so the bucket must allow the app's origin and the {@code Content-Type} header
 * or the upload is blocked. This runs on startup only when {@code buddydrop.storage.configure-cors}
 * is enabled; it is idempotent, and if the credentials lack {@code s3:PutBucketCORS} it logs the
 * exact policy to apply by hand rather than failing the app.
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "buddydrop.storage", name = "configure-cors", havingValue = "true")
public class BucketCorsConfigurer {

    private final S3Client s3;
    private final AppProperties properties;

    public BucketCorsConfigurer(S3Client s3, AppProperties properties) {
        this.s3 = s3;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void applyCors() {
        String bucket = properties.getStorage().getBucket();
        String origin = origin(properties.getBaseUrl());

        CORSRule rule = CORSRule.builder()
                .allowedOrigins(origin)
                .allowedMethods("PUT", "GET", "HEAD")
                .allowedHeaders("*")
                .exposeHeaders("ETag")
                .maxAgeSeconds(3000)
                .build();

        try {
            s3.putBucketCors(PutBucketCorsRequest.builder()
                    .bucket(bucket)
                    .corsConfiguration(CORSConfiguration.builder().corsRules(rule).build())
                    .build());
            log.info("Applied CORS to bucket '{}' allowing uploads from {}", bucket, origin);
        } catch (Exception e) {
            String policy = ("[{\"AllowedOrigins\":[\"" + origin + "\"],"
                    + "\"AllowedMethods\":[\"PUT\",\"GET\",\"HEAD\"],"
                    + "\"AllowedHeaders\":[\"*\"],\"ExposeHeaders\":[\"ETag\"],\"MaxAgeSeconds\":3000}]");
            log.warn("Could not set CORS on bucket '{}' ({}). Browser uploads will be blocked until CORS "
                    + "is configured. Apply this rule by hand (S3 console -> bucket -> Permissions -> CORS): {}",
                    bucket, e.getMessage(), policy);
        }
    }

    /** Reduce a base URL to its origin (scheme://host[:port]) for the CORS AllowedOrigins list. */
    private static String origin(String baseUrl) {
        URI uri = URI.create(baseUrl);
        String scheme = uri.getScheme();
        String host = uri.getHost();
        int port = uri.getPort();
        return port < 0 ? scheme + "://" + host : scheme + "://" + host + ":" + port;
    }
}
