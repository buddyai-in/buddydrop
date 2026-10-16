package com.buddyai.buddydrop.auth.social;

import com.buddyai.buddydrop.config.AppProperties;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The social sign-in providers this deployment offers, built from {@code buddydrop.auth.social.*}.
 *
 * <p>A provider is registered only when its client id is set, so a deployment with no social
 * credentials behaves exactly as before (magic link only). Endpoints live here rather than in
 * configuration so operators supply credentials alone. Partially configured providers fail startup
 * instead of failing at a user's first click.
 *
 * <p>Unlike Spring Boot's own repository this one may be empty; {@code SocialLoginConfigurer} only
 * enables OAuth2 login when {@link #isEmpty()} is false.
 */
@Component
public class SocialClientRegistrations implements ClientRegistrationRepository, Iterable<ClientRegistration> {

    public static final String GOOGLE = "google";
    public static final String APPLE = "apple";
    public static final String FACEBOOK = "facebook";
    public static final String X = "x";

    /** Spring's callback path; the full URI must be registered verbatim with each provider. */
    static final String CALLBACK_PATH = "/login/oauth2/code/";

    private final Map<String, ClientRegistration> registrations;

    public SocialClientRegistrations(AppProperties properties) {
        String baseUrl = properties.getBaseUrl().replaceAll("/+$", "");
        AppProperties.Social social = properties.getAuth().getSocial();

        Map<String, ClientRegistration> byId = new LinkedHashMap<>();
        // Insertion order is the button order on the sign-in page.
        Stream.of(
                        google(social.getGoogle(), baseUrl),
                        apple(social.getApple(), baseUrl),
                        facebook(social.getFacebook(), baseUrl),
                        x(social.getX(), baseUrl))
                .filter(r -> r != null)
                .forEach(r -> byId.put(r.getRegistrationId(), r));
        this.registrations = Collections.unmodifiableMap(byId);
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        return registrations.get(registrationId);
    }

    @Override
    public Iterator<ClientRegistration> iterator() {
        return registrations.values().iterator();
    }

    public boolean isEmpty() {
        return registrations.isEmpty();
    }

    /** Enabled providers in display order, for the sign-in page. */
    public List<SocialProvider> providers() {
        List<SocialProvider> providers = new ArrayList<>();
        registrations.values().forEach(r -> providers.add(new SocialProvider(r.getRegistrationId(), r.getClientName())));
        return providers;
    }

    private static ClientRegistration google(AppProperties.OAuthClient client, String baseUrl) {
        if (!requireSecret(GOOGLE, client)) {
            return null;
        }
        return CommonOAuth2Provider.GOOGLE.getBuilder(GOOGLE)
                .clientId(client.getClientId())
                .clientSecret(client.getClientSecret())
                .redirectUri(baseUrl + CALLBACK_PATH + GOOGLE)
                .scope("openid", "email")
                .build();
    }

    private static ClientRegistration facebook(AppProperties.OAuthClient client, String baseUrl) {
        if (!requireSecret(FACEBOOK, client)) {
            return null;
        }
        return CommonOAuth2Provider.FACEBOOK.getBuilder(FACEBOOK)
                .clientId(client.getClientId())
                .clientSecret(client.getClientSecret())
                .redirectUri(baseUrl + CALLBACK_PATH + FACEBOOK)
                .scope("email")
                .build();
    }

    /**
     * X OAuth 2.0 (not OIDC). X requires PKCE even for confidential clients, and only returns
     * {@code confirmed_email} with the {@code users.email} scope (which itself requires
     * {@code tweet.read} + {@code users.read}).
     */
    private static ClientRegistration x(AppProperties.OAuthClient client, String baseUrl) {
        if (!requireSecret(X, client)) {
            return null;
        }
        return ClientRegistration.withRegistrationId(X)
                .clientName("X")
                .clientId(client.getClientId())
                .clientSecret(client.getClientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(baseUrl + CALLBACK_PATH + X)
                .scope("tweet.read", "users.read", "users.email")
                .authorizationUri("https://x.com/i/oauth2/authorize")
                .tokenUri("https://api.x.com/2/oauth2/token")
                .userInfoUri("https://api.x.com/2/users/me?user.fields=confirmed_email")
                .userNameAttributeName("id")
                .clientSettings(ClientRegistration.ClientSettings.builder().requireProofKey(true).build())
                .build();
    }

    /**
     * Sign in with Apple (OIDC). There is no userinfo endpoint — the email arrives in the ID token.
     * The client secret is left blank here and minted per token request by {@link AppleClientSecret}.
     */
    private static ClientRegistration apple(AppProperties.Apple apple, String baseUrl) {
        if (!StringUtils.hasText(apple.getClientId())) {
            return null;
        }
        if (!StringUtils.hasText(apple.getTeamId())
                || !StringUtils.hasText(apple.getKeyId())
                || !StringUtils.hasText(apple.getPrivateKey())) {
            throw new IllegalStateException("Sign in with Apple needs buddydrop.auth.social.apple.team-id, "
                    + "key-id and private-key in addition to client-id");
        }
        return ClientRegistration.withRegistrationId(APPLE)
                .clientName("Apple")
                .clientId(apple.getClientId())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(baseUrl + CALLBACK_PATH + APPLE)
                .scope("openid", "email")
                .authorizationUri("https://appleid.apple.com/auth/authorize")
                .tokenUri("https://appleid.apple.com/auth/token")
                .jwkSetUri("https://appleid.apple.com/auth/keys")
                .issuerUri("https://appleid.apple.com")
                .userNameAttributeName("sub")
                .build();
    }

    /** @return false when the provider is not configured at all; throws when it is half-configured. */
    private static boolean requireSecret(String id, AppProperties.OAuthClient client) {
        if (!StringUtils.hasText(client.getClientId())) {
            return false;
        }
        if (!StringUtils.hasText(client.getClientSecret())) {
            throw new IllegalStateException(
                    "buddydrop.auth.social." + id + ".client-secret is required when client-id is set");
        }
        return true;
    }
}
