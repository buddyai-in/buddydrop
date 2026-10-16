package com.buddyai.buddydrop.auth.social;

import com.buddyai.buddydrop.support.MutableClock;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BrowserBoundAuthorizationRequestRepositoryTest {

    private final BrowserBoundAuthorizationRequestRepository repository =
            new BrowserBoundAuthorizationRequestRepository(Clock.systemUTC());

    @Test
    void secureRequestsGetASameSiteNoneHandleCookie() {
        MockHttpServletRequest start = new MockHttpServletRequest();
        start.setSecure(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveAuthorizationRequest(authorizationRequest("state-1"), start, response);

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .startsWith(BrowserBoundAuthorizationRequestRepository.COOKIE_NAME + "=")
                .contains("Path=/login/oauth2/code/", "HttpOnly", "Secure", "SameSite=None");
    }

    @Test
    void loadsOnlyWithMatchingCookieAndState() {
        Cookie handle = save("state-1");

        assertThat(repository.loadAuthorizationRequest(callback(handle, "state-1"))).isNotNull();
        assertThat(repository.loadAuthorizationRequest(callback(handle, "other-state"))).isNull();
        assertThat(repository.loadAuthorizationRequest(callback(null, "state-1"))).isNull();
        assertThat(repository.loadAuthorizationRequest(
                callback(new Cookie(handle.getName(), "forged"), "state-1"))).isNull();
    }

    @Test
    void removeIsSingleUse() {
        Cookie handle = save("state-1");

        assertThat(repository.removeAuthorizationRequest(callback(handle, "state-1"), new MockHttpServletResponse()))
                .isNotNull();
        assertThat(repository.loadAuthorizationRequest(callback(handle, "state-1"))).isNull();
    }

    @Test
    void expiredRequestsAreNotLoaded() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        var expiring = new BrowserBoundAuthorizationRequestRepository(clock);
        MockHttpServletResponse response = new MockHttpServletResponse();
        expiring.saveAuthorizationRequest(authorizationRequest("s"), new MockHttpServletRequest(), response);
        Cookie handle = response.getCookie(BrowserBoundAuthorizationRequestRepository.COOKIE_NAME);

        assertThat(expiring.loadAuthorizationRequest(callback(handle, "s"))).isNotNull();
        clock.advance(BrowserBoundAuthorizationRequestRepository.TTL);
        assertThat(expiring.loadAuthorizationRequest(callback(handle, "s"))).isNull();
    }

    private Cookie save(String state) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(authorizationRequest(state), new MockHttpServletRequest(), response);
        return response.getCookie(BrowserBoundAuthorizationRequestRepository.COOKIE_NAME);
    }

    private static MockHttpServletRequest callback(Cookie cookie, String state) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login/oauth2/code/apple");
        if (cookie != null) {
            request.setCookies(cookie);
        }
        request.setParameter("state", state);
        return request;
    }

    private static OAuth2AuthorizationRequest authorizationRequest(String state) {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://provider.test/authorize")
                .clientId("client")
                .redirectUri("http://localhost:8080/login/oauth2/code/apple")
                .state(state)
                .build();
    }
}
