package com.buddyai.buddydrop.auth.social;

import com.buddyai.buddydrop.config.AppProperties;
import com.buddyai.buddydrop.support.MutableClock;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppleClientSecretTest {

    private KeyPair keyPair;
    private AppProperties.Apple apple;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        keyPair = generator.generateKeyPair();

        apple = new AppProperties.Apple();
        apple.setClientId("app.buddydrop.signin");
        apple.setTeamId("TEAMID1234");
        apple.setKeyId("KEYID12345");
        // As pasted into an env file: PEM with literal "\n" separators.
        apple.setPrivateKey("-----BEGIN PRIVATE KEY-----\\n"
                + Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded())
                + "\\n-----END PRIVATE KEY-----");
    }

    @Test
    void mintsAnAppleShapedEs256Jwt() throws Exception {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        String secret = new AppleClientSecret(apple, Clock.fixed(now, ZoneOffset.UTC)).get();

        SignedJWT jwt = SignedJWT.parse(secret);
        assertThat(jwt.verify(new ECDSAVerifier((ECPublicKey) keyPair.getPublic()))).isTrue();
        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
        assertThat(jwt.getHeader().getKeyID()).isEqualTo("KEYID12345");
        assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("TEAMID1234");
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo("app.buddydrop.signin");
        assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly("https://appleid.apple.com");
        assertThat(jwt.getJWTClaimsSet().getExpirationTime().toInstant()).isEqualTo(now.plus(AppleClientSecret.LIFETIME));
    }

    @Test
    void reusesSecretUntilNearExpiry() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        AppleClientSecret secret = new AppleClientSecret(apple, clock);

        String first = secret.get();
        clock.advance(Duration.ofDays(1));
        assertThat(secret.get()).isEqualTo(first);

        clock.advance(AppleClientSecret.LIFETIME);
        assertThat(secret.get()).isNotEqualTo(first);
    }

    @Test
    void malformedKeyFailsFast() {
        apple.setPrivateKey("not-a-key");
        assertThatThrownBy(() -> new AppleClientSecret(apple, Clock.systemUTC()))
                .hasMessageContaining("private-key");
    }
}
