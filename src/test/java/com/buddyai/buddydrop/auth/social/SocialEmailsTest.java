package com.buddyai.buddydrop.auth.social;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SocialEmailsTest {

    @Test
    void oidcVerifiedEmailIsAcceptedAndNormalized() {
        assertThat(SocialEmails.verifiedEmail("google", oidc(Map.of("email", "Ann@Example.com", "email_verified", true))))
                .contains("ann@example.com");
    }

    @Test
    void oidcUnverifiedEmailIsRejected() {
        assertThat(SocialEmails.verifiedEmail("google", oidc(Map.of("email", "ann@example.com", "email_verified", false))))
                .isEmpty();
        assertThat(SocialEmails.verifiedEmail("google", oidc(Map.of("email", "ann@example.com"))))
                .isEmpty();
    }

    @Test
    void appleStringBooleanIsUnderstood() {
        // Apple has historically sent email_verified as the string "true".
        assertThat(SocialEmails.verifiedEmail("apple", oidc(Map.of("email", "relay@privaterelay.appleid.com", "email_verified", "true"))))
                .contains("relay@privaterelay.appleid.com");
    }

    @Test
    void facebookEmailIsAccepted() {
        assertThat(SocialEmails.verifiedEmail("facebook", oauth2(Map.of("id", "1", "email", "fb@example.com"))))
                .contains("fb@example.com");
    }

    @Test
    void xConfirmedEmailIsAccepted() {
        assertThat(SocialEmails.verifiedEmail("x", oauth2(Map.of("id", "1", "confirmed_email", "x@example.com"))))
                .contains("x@example.com");
    }

    @Test
    void missingEmailIsRejected() {
        assertThat(SocialEmails.verifiedEmail("facebook", oauth2(Map.of("id", "1")))).isEmpty();
        assertThat(SocialEmails.verifiedEmail("x", oauth2(Map.of("id", "1", "email", "unconfirmed@example.com")))).isEmpty();
    }

    private static OAuth2User oidc(Map<String, Object> claims) {
        OidcIdToken token = OidcIdToken.withTokenValue("id-token")
                .subject("sub-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claims(c -> c.putAll(claims))
                .build();
        return new DefaultOidcUser(AuthorityUtils.NO_AUTHORITIES, token);
    }

    private static OAuth2User oauth2(Map<String, Object> attributes) {
        return new DefaultOAuth2User(AuthorityUtils.NO_AUTHORITIES, attributes, "id");
    }
}
