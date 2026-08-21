package com.buddyai.buddydrop.mail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Dev/default transport: logs the message instead of sending it, so the app runs with no mail
 * infrastructure. Active when {@code buddydrop.mail.provider=log} or unset. The magic-link URL lives
 * in the logged HTML body, so a developer can copy it straight from the console to sign in.
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "buddydrop.mail", name = "provider", havingValue = "log", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

    @Override
    public void send(String to, String subject, String htmlBody) {
        log.warn("[DEV EMAIL — not actually sent] to={} subject=\"{}\"\n{}", to, subject, htmlBody);
    }
}
