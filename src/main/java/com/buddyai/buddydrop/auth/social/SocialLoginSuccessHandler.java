package com.buddyai.buddydrop.auth.social;

import com.buddyai.buddydrop.auth.AccountService;
import com.buddyai.buddydrop.domain.AppUser;
import com.buddyai.buddydrop.security.SessionAuthenticator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

/**
 * Turns a completed provider sign-in into a normal BuddyDrop session.
 *
 * <p>Spring Security leaves an {@link OAuth2AuthenticationToken} in the context; the rest of the app
 * only understands {@code AppUserPrincipal}. So we resolve the provider-verified email, find-or-create
 * that account, and replace the authentication via {@link SessionAuthenticator} — the same call the
 * magic-link flow ends with. Without a verified email the half-made session is discarded.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SocialLoginSuccessHandler implements AuthenticationSuccessHandler {

    static final String NO_EMAIL_REDIRECT = "/login?error=social-email";

    private final AccountService accounts;
    private final SessionAuthenticator sessionAuthenticator;
    private final SecurityContextLogoutHandler discardSession = new SecurityContextLogoutHandler();

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        String provider = token.getAuthorizedClientRegistrationId();

        Optional<String> email = SocialEmails.verifiedEmail(provider, token.getPrincipal());
        if (email.isEmpty()) {
            log.info("Rejected {} sign-in without a verified email", provider);
            discardSession.logout(request, response, authentication);
            response.sendRedirect(request.getContextPath() + NO_EMAIL_REDIRECT);
            return;
        }

        AppUser user = accounts.signIn(email.get());
        sessionAuthenticator.signIn(user, request, response);
        log.info("Signed in {} via {}", user.getEmail(), provider);
        response.sendRedirect(request.getContextPath() + "/files");
    }
}
