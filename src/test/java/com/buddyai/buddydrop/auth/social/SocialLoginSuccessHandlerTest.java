package com.buddyai.buddydrop.auth.social;

import com.buddyai.buddydrop.domain.AppUser;
import com.buddyai.buddydrop.repository.AppUserRepository;
import com.buddyai.buddydrop.security.AppUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SocialLoginSuccessHandlerTest {

    @Autowired SocialLoginSuccessHandler handler;
    @Autowired AppUserRepository users;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void verifiedEmailCreatesAccountAndOpensAppSession() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response,
                token("facebook", Map.of("id", "fb-1", "email", "New@Example.com")));

        AppUser user = users.findByEmail("new@example.com").orElseThrow();
        assertThat(user.getLastLoginAt()).isNotNull();
        assertThat(response.getRedirectedUrl()).isEqualTo("/files");

        SecurityContext saved = (SecurityContext) request.getSession()
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(saved.getAuthentication().getPrincipal())
                .isEqualTo(new AppUserPrincipal(user.getId(), "new@example.com"));
    }

    @Test
    void sameEmailSignsIntoExistingMagicLinkAccount() throws Exception {
        AppUser existing = users.save(AppUser.builder().email("ann@example.com").quotaBytes(1_000).build());

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), new MockHttpServletResponse(),
                token("x", Map.of("id", "x-1", "confirmed_email", "ann@example.com")));

        assertThat(users.findAll()).extracting(AppUser::getId).containsExactly(existing.getId());
    }

    @Test
    void missingVerifiedEmailDiscardsSessionAndExplains() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, token("facebook", Map.of("id", "fb-2")));

        assertThat(response.getRedirectedUrl()).isEqualTo(SocialLoginSuccessHandler.NO_EMAIL_REDIRECT);
        assertThat(request.getSession(false)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(users.findAll()).isEmpty();
    }

    private static OAuth2AuthenticationToken token(String provider, Map<String, Object> attributes) {
        return new OAuth2AuthenticationToken(
                new DefaultOAuth2User(AuthorityUtils.createAuthorityList("OAUTH2_USER"), attributes, "id"),
                AuthorityUtils.createAuthorityList("OAUTH2_USER"), provider);
    }
}
