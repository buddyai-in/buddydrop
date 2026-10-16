package com.buddyai.buddydrop.auth.social;

import com.buddyai.buddydrop.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SocialLoginConfigurerTest {

    private final SocialClientRegistrations registrations = new SocialClientRegistrations(properties());

    @Test
    void xUserIsFlattenedOutOfDataEnvelope() {
        Map<String, Object> flat = SocialLoginConfigurer.attributesConverter(request("x"))
                .convert(Map.of("data", Map.of("id", "42", "username", "ann", "confirmed_email", "ann@example.com")));

        assertThat(flat).containsEntry("id", "42").containsEntry("confirmed_email", "ann@example.com");
    }

    @Test
    void otherProvidersAreUntouched() {
        Map<String, Object> attributes = Map.of("id", "1", "data", "unrelated");
        assertThat(SocialLoginConfigurer.attributesConverter(request("facebook")).convert(attributes))
                .isSameAs(attributes);
    }

    private OAuth2UserRequest request(String registrationId) {
        ClientRegistration registration = registrations.findByRegistrationId(registrationId);
        return new OAuth2UserRequest(registration, new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "t", Instant.now(), Instant.now().plusSeconds(60)));
    }

    private static AppProperties properties() {
        AppProperties properties = new AppProperties();
        AppProperties.Social social = properties.getAuth().getSocial();
        social.getX().setClientId("x-id");
        social.getX().setClientSecret("x-secret");
        social.getFacebook().setClientId("fb-id");
        social.getFacebook().setClientSecret("fb-secret");
        return properties;
    }
}
