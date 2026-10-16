package com.buddyai.buddydrop.auth.social;

import com.buddyai.buddydrop.config.AppProperties;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/**
 * Wires "Continue with Google / Apple / Facebook / X" into the security filter chain. Every
 * provider-specific quirk is adapted here, so the rest of the flow is plain Spring OAuth2 login:
 *
 * <ul>
 *   <li><b>Apple</b> — {@code response_mode=form_post} (mandatory when requesting email) on the
 *       authorization request, and a freshly signed JWT as {@code client_secret} on the token request.</li>
 *   <li><b>X</b> — PKCE is declared on its registration; its userinfo nests the user under
 *       {@code data}, which is flattened here so {@code id} / {@code confirmed_email} are top-level.</li>
 * </ul>
 *
 * <p>Does nothing when no provider is configured, so the app stays magic-link only by default.
 */
@Component
public class SocialLoginConfigurer {

    private final SocialClientRegistrations registrations;
    private final SocialLoginSuccessHandler successHandler;
    private final AppleClientSecret appleClientSecret;

    public SocialLoginConfigurer(SocialClientRegistrations registrations,
                                 SocialLoginSuccessHandler successHandler,
                                 AppProperties properties) {
        this.registrations = registrations;
        this.successHandler = successHandler;
        this.appleClientSecret = registrations.findByRegistrationId(SocialClientRegistrations.APPLE) == null
                ? null
                : new AppleClientSecret(properties.getAuth().getSocial().getApple(), Clock.systemUTC());
    }

    /** The provider callback path, exempt from CSRF: {@code state} + the handle cookie protect it. */
    public static String callbackPattern() {
        return SocialClientRegistrations.CALLBACK_PATH + "*";
    }

    public void apply(HttpSecurity http) throws Exception {
        if (registrations.isEmpty()) {
            return;
        }
        http.oauth2Login(oauth -> oauth
                .loginPage("/login")
                .clientRegistrationRepository(registrations)
                .authorizationEndpoint(endpoint -> endpoint
                        .authorizationRequestResolver(authorizationRequestResolver())
                        .authorizationRequestRepository(new BrowserBoundAuthorizationRequestRepository(Clock.systemUTC())))
                .tokenEndpoint(token -> token.accessTokenResponseClient(tokenResponseClient()))
                .userInfoEndpoint(userInfo -> userInfo.userService(userService()))
                .successHandler(successHandler)
                .failureUrl("/login?error=social"));
    }

    private DefaultOAuth2AuthorizationRequestResolver authorizationRequestResolver() {
        var resolver = new DefaultOAuth2AuthorizationRequestResolver(
                registrations, OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
        resolver.setAuthorizationRequestCustomizer(builder -> builder.attributes(attributes -> {
            if (SocialClientRegistrations.APPLE.equals(attributes.get(OAuth2ParameterNames.REGISTRATION_ID))) {
                builder.additionalParameters(params -> params.put("response_mode", "form_post"));
            }
        }));
        return resolver;
    }

    private RestClientAuthorizationCodeTokenResponseClient tokenResponseClient() {
        var client = new RestClientAuthorizationCodeTokenResponseClient();
        if (appleClientSecret != null) {
            // Overrides the (blank) static secret only for Apple; other providers are untouched.
            client.addParametersConverter(request -> {
                if (!SocialClientRegistrations.APPLE.equals(request.getClientRegistration().getRegistrationId())) {
                    return null;
                }
                MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
                params.set(OAuth2ParameterNames.CLIENT_SECRET, appleClientSecret.get());
                return params;
            });
        }
        return client;
    }

    private DefaultOAuth2UserService userService() {
        var service = new DefaultOAuth2UserService();
        service.setAttributesConverter(SocialLoginConfigurer::attributesConverter);
        return service;
    }

    /** X wraps the user as {@code {"data": {...}}}; everyone else is already flat. */
    static Converter<Map<String, Object>, Map<String, Object>> attributesConverter(OAuth2UserRequest request) {
        if (!SocialClientRegistrations.X.equals(request.getClientRegistration().getRegistrationId())) {
            return attributes -> attributes;
        }
        return attributes -> attributes.get("data") instanceof Map<?, ?> data
                ? flatten(data)
                : attributes;
    }

    private static Map<String, Object> flatten(Map<?, ?> data) {
        Map<String, Object> flat = new HashMap<>();
        data.forEach((k, v) -> flat.put(String.valueOf(k), v));
        return flat;
    }
}
