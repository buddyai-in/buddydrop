package com.buddyai.buddydrop.auth.social;

import com.buddyai.buddydrop.util.Tokens;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Holds the in-flight OAuth2 authorization request (state, nonce, PKCE verifier) between the redirect
 * to the provider and its callback.
 *
 * <p>Why not the HTTP session (Spring's default): Sign in with Apple returns via a cross-site
 * {@code form_post}, and browsers do not send our {@code SameSite=Lax} session cookie on that POST — so
 * a session-held request would be lost. Instead the request stays server-side, and the browser holds
 * only a random handle in a short-lived, HttpOnly cookie scoped to the callback path, marked
 * {@code SameSite=None} (on HTTPS) so it survives the POST. Nothing is deserialized from the client.
 *
 * <p>The callback is accepted only when the handle cookie <em>and</em> the returned {@code state} both
 * match, which binds the response to the browser that started the flow (login-CSRF protection). Kept
 * in-process like sessions and {@code RateLimitService}: a single node needs no shared store.
 */
class BrowserBoundAuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    static final String COOKIE_NAME = "BD_OAUTH2_REQ";
    static final Duration TTL = Duration.ofMinutes(10);
    /** Upper bound on concurrently pending sign-ins, so abandoned redirects cannot grow memory without limit. */
    static final int MAX_PENDING = 10_000;
    private static final String COOKIE_PATH = SocialClientRegistrations.CALLBACK_PATH;

    private final ConcurrentMap<String, Pending> pending = new ConcurrentHashMap<>();
    private final Clock clock;

    BrowserBoundAuthorizationRequestRepository(Clock clock) {
        this.clock = clock;
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String key = handleKey(request);
        if (key == null) {
            return null;
        }
        Pending entry = pending.get(key);
        if (entry == null || entry.isExpired(clock.instant())) {
            return null;
        }
        String state = request.getParameter(OAuth2ParameterNames.STATE);
        return Objects.equals(state, entry.request().getState()) ? entry.request() : null;
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            removeAuthorizationRequest(request, response);
            return;
        }
        Instant now = clock.instant();
        pending.values().removeIf(p -> p.isExpired(now));
        if (pending.size() >= MAX_PENDING) {
            throw new IllegalStateException("Too many social sign-ins in progress");
        }
        String handle = Tokens.generate();
        pending.put(Tokens.hash(handle), new Pending(authorizationRequest, now.plus(TTL)));
        writeCookie(request, response, handle, TTL);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                 HttpServletResponse response) {
        OAuth2AuthorizationRequest loaded = loadAuthorizationRequest(request);
        if (loaded != null) {
            pending.remove(handleKey(request));
            writeCookie(request, response, "", Duration.ZERO);
        }
        return loaded;
    }

    /** Hash of the handle cookie — the raw handle is never stored, mirroring {@link Tokens} usage elsewhere. */
    private static String handleKey(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return Tokens.hash(cookie.getValue());
            }
        }
        return null;
    }

    /**
     * {@code SameSite=None} requires {@code Secure}; over plain HTTP (local dev) fall back to
     * {@code Lax}, which still covers the GET callbacks of Google, Facebook and X.
     */
    private static void writeCookie(HttpServletRequest request, HttpServletResponse response,
                                    String value, Duration maxAge) {
        boolean secure = request.isSecure();
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, value)
                .path(COOKIE_PATH)
                .httpOnly(true)
                .secure(secure)
                .sameSite(secure ? "None" : "Lax")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private record Pending(OAuth2AuthorizationRequest request, Instant expiresAt) {
        boolean isExpired(Instant now) {
            return !now.isBefore(expiresAt);
        }
    }
}
