package com.buddyai.buddydrop.mail;

import com.buddyai.buddydrop.config.AppProperties;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Sends transactional email. Today that is only the magic-link sign-in message.
 *
 * <p>{@link JavaMailSender} is optional: when no SMTP host is configured (typical for local dev),
 * the sign-in link is logged instead of sent, so the app is fully usable without a mail server.
 * This avoids a hard startup dependency on SMTP while keeping production behavior unchanged.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final AppProperties properties;

    public void sendMagicLink(String toEmail, String link) {
        String subject = "Your BuddyDrop sign-in link";
        String html = """
                <div style="font-family:Arial,Helvetica,sans-serif;max-width:480px;margin:0 auto;color:#0e1b1d">
                  <h2 style="color:#0d7c86">Sign in to BuddyDrop</h2>
                  <p>Click the button below to sign in. This link expires shortly and can be used once.</p>
                  <p style="margin:28px 0">
                    <a href="%s" style="background:#0d7c86;color:#fff;text-decoration:none;
                       padding:12px 22px;border-radius:8px;font-weight:bold;display:inline-block">
                       Sign in to BuddyDrop</a>
                  </p>
                  <p style="color:#5f767b;font-size:13px">If you didn't request this, you can safely ignore this email.</p>
                </div>
                """.formatted(link);

        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("No mail sender configured — magic-link for {} (dev mode): {}", toEmail, link);
            return;
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setTo(toEmail);
            helper.setFrom(properties.getMail().getFrom(), properties.getMail().getFromName());
            helper.setSubject(subject);
            helper.setText(html, true);
            sender.send(message);
            log.info("Sent magic-link email to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send magic-link email to {}", toEmail, e);
            throw new IllegalStateException("Could not send sign-in email", e);
        }
    }
}
