package com.buddyai.buddydrop.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

/**
 * Provides the SES client, only when {@code buddydrop.mail.provider=ses}. Credentials come from the
 * standard {@link DefaultCredentialsProvider} chain (env, profile, IAM role) — the same chain S3 uses,
 * so no separate mail secrets are required.
 */
@Configuration
@ConditionalOnProperty(prefix = "buddydrop.mail", name = "provider", havingValue = "ses")
public class SesConfig {

    @Bean
    public SesV2Client sesV2Client(AppProperties properties) {
        return SesV2Client.builder()
                .region(Region.of(properties.getMail().getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
