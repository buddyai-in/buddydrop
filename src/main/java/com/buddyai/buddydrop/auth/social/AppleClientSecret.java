package com.buddyai.buddydrop.auth.social;

import com.buddyai.buddydrop.config.AppProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Mints the {@code client_secret} Apple requires on token requests: an ES256 JWT signed with the
 * team's Sign in with Apple key ({@code iss}=team id, {@code sub}=Services ID,
 * {@code aud}=https://appleid.apple.com). Apple caps its lifetime at six months; we mint a shorter one
 * and re-mint shortly before it lapses, so a long-running node never presents an expired secret.
 *
 * <p>The key is parsed eagerly so a malformed key fails startup rather than the first Apple sign-in.
 */
final class AppleClientSecret {

    static final String AUDIENCE = "https://appleid.apple.com";
    static final Duration LIFETIME = Duration.ofDays(30);
    private static final Duration RENEW_BEFORE = Duration.ofDays(1);

    private final String teamId;
    private final String keyId;
    private final String clientId;
    private final ECPrivateKey privateKey;
    private final Clock clock;

    private Minted current;

    AppleClientSecret(AppProperties.Apple apple, Clock clock) {
        this.teamId = apple.getTeamId();
        this.keyId = apple.getKeyId();
        this.clientId = apple.getClientId();
        this.privateKey = parsePrivateKey(apple.getPrivateKey());
        this.clock = clock;
    }

    /** A valid client secret, reusing the cached one until it nears expiry. */
    synchronized String get() {
        Instant now = clock.instant();
        if (current == null || now.isAfter(current.expiresAt().minus(RENEW_BEFORE))) {
            current = mint(now);
        }
        return current.jwt();
    }

    private Minted mint(Instant now) {
        Instant expiresAt = now.plus(LIFETIME);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(teamId)
                .subject(clientId)
                .audience(AUDIENCE)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(keyId).build(), claims);
        try {
            jwt.sign(new ECDSASigner(privateKey));
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign the Sign in with Apple client secret", e);
        }
        return new Minted(jwt.serialize(), expiresAt);
    }

    /**
     * Accepts the {@code .p8} file contents as PEM, or just its base64 body — and tolerates literal
     * {@code \n} sequences, since multi-line values are awkward in env files.
     */
    static ECPrivateKey parsePrivateKey(String pem) {
        String body = pem
                .replace("\\n", "\n")
                .replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        try {
            byte[] der = Base64.getDecoder().decode(body);
            return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "buddydrop.auth.social.apple.private-key is not a valid PKCS#8 EC key (the AuthKey_*.p8 file)", e);
        }
    }

    private record Minted(String jwt, Instant expiresAt) {
    }
}
