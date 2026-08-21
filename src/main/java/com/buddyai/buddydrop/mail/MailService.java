package com.buddyai.buddydrop.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Composes transactional email and delegates delivery to the configured {@link EmailSender}
 * (SES, SMTP, or a dev logger). Today the only message is the magic-link sign-in email.
 *
 * <p>Keeping content here and transport behind {@link EmailSender} means switching providers is a
 * configuration change ({@code buddydrop.mail.provider}), not a code change — and the email markup
 * lives in exactly one place.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MailService {

    private final EmailSender emailSender;

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

        emailSender.send(toEmail, subject, html);
    }
}
