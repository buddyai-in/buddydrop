package com.buddyai.buddydrop.auth.social;

import com.buddyai.buddydrop.auth.AccountService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * Extracts the email a provider <em>vouches for</em>. Accounts are keyed by email, so an unverified
 * address must never be accepted — that would let anyone who types a victim's address at a provider
 * take over the victim's BuddyDrop account.
 *
 * <ul>
 *   <li>OIDC providers (Google, Apple): the standard {@code email} + {@code email_verified} claims.</li>
 *   <li>Facebook: the Graph API only returns {@code email} when it is confirmed.</li>
 *   <li>X: {@code confirmed_email} (flattened out of the {@code data} envelope by the user service).</li>
 * </ul>
 */
final class SocialEmails {

    private SocialEmails() {
    }

    static Optional<String> verifiedEmail(String registrationId, OAuth2User user) {
        String email;
        if (user instanceof OidcUser oidc) {
            email = Boolean.TRUE.equals(oidc.getEmailVerified()) ? oidc.getEmail() : null;
        } else {
            email = switch (registrationId) {
                case SocialClientRegistrations.FACEBOOK -> user.getAttribute("email");
                case SocialClientRegistrations.X -> user.getAttribute("confirmed_email");
                default -> null;
            };
        }
        return StringUtils.hasText(email)
                ? Optional.of(AccountService.normalizeEmail(email))
                : Optional.empty();
    }
}
