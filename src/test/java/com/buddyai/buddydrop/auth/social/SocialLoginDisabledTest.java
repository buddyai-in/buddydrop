package com.buddyai.buddydrop.auth.social;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The default deployment has no social credentials: the app must boot and stay magic-link only. */
@SpringBootTest(properties = {
        "buddydrop.auth.social.google.client-id=",
        "buddydrop.auth.social.facebook.client-id=",
        "buddydrop.auth.social.x.client-id=",
        "buddydrop.auth.social.apple.client-id="})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SocialLoginDisabledTest {

    @Autowired MockMvc mvc;

    @Test
    void loginPageShowsOnlyMagicLink() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Send me a magic link")))
                .andExpect(content().string(not(containsString("/oauth2/authorization/"))));
    }

    @Test
    void authorizationEndpointIsNotActive() throws Exception {
        // Without oauth2Login the path is just an unauthenticated app URL → bounced to the login page.
        mvc.perform(get("/oauth2/authorization/google"))
                .andExpect(redirectedUrl("/login"));
    }
}
