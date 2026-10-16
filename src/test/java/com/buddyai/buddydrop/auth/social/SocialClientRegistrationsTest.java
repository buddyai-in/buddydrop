package com.buddyai.buddydrop.auth.social;

import com.buddyai.buddydrop.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SocialClientRegistrationsTest {

    @Test
    void noCredentialsMeansNoProviders() {
        SocialClientRegistrations registrations = new SocialClientRegistrations(new AppProperties());
        assertThat(registrations.isEmpty()).isTrue();
        assertThat(registrations.providers()).isEmpty();
    }

    @Test
    void onlyConfiguredProvidersAreRegisteredWithBaseUrlCallback() {
        AppProperties properties = new AppProperties();
        properties.setBaseUrl("https://drop.example.com/");
        properties.getAuth().getSocial().getGoogle().setClientId("g");
        properties.getAuth().getSocial().getGoogle().setClientSecret("s");

        SocialClientRegistrations registrations = new SocialClientRegistrations(properties);

        assertThat(registrations.providers()).containsExactly(new SocialProvider("google", "Google"));
        assertThat(registrations.findByRegistrationId("google").getRedirectUri())
                .isEqualTo("https://drop.example.com/login/oauth2/code/google");
        assertThat(registrations.findByRegistrationId("x")).isNull();
    }

    @Test
    void halfConfiguredProviderFailsFast() {
        AppProperties properties = new AppProperties();
        properties.getAuth().getSocial().getFacebook().setClientId("fb");
        assertThatThrownBy(() -> new SocialClientRegistrations(properties))
                .hasMessageContaining("facebook.client-secret");

        AppProperties apple = new AppProperties();
        apple.getAuth().getSocial().getApple().setClientId("app.buddydrop");
        assertThatThrownBy(() -> new SocialClientRegistrations(apple))
                .hasMessageContaining("team-id");
    }
}
