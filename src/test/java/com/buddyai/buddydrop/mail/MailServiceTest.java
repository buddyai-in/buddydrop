package com.buddyai.buddydrop.mail;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MailServiceTest {

    /** Captures what MailService hands to the transport. */
    static class CapturingSender implements EmailSender {
        record Sent(String to, String subject, String body) {
        }
        final List<Sent> sent = new ArrayList<>();
        @Override
        public void send(String to, String subject, String htmlBody) {
            sent.add(new Sent(to, subject, htmlBody));
        }
    }

    @Test
    void magicLinkEmailCarriesSubjectRecipientAndLink() {
        CapturingSender sender = new CapturingSender();
        MailService mailService = new MailService(sender);

        String link = "https://buddydrop.app/auth/verify?token=abc123";
        mailService.sendMagicLink("user@example.com", link);

        assertThat(sender.sent).hasSize(1);
        CapturingSender.Sent sent = sender.sent.get(0);
        assertThat(sent.to()).isEqualTo("user@example.com");
        assertThat(sent.subject()).isEqualTo("Your BuddyDrop sign-in link");
        assertThat(sent.body()).contains(link);          // the link is embedded in the HTML
        assertThat(sent.body()).contains("Sign in to BuddyDrop");
    }
}
