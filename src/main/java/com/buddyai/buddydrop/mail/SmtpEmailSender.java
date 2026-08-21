package com.buddyai.buddydrop.mail;

import com.buddyai.buddydrop.config.AppProperties;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Sends email over SMTP via Spring's {@link JavaMailSender}. Active when
 * {@code buddydrop.mail.provider=smtp}. Suitable for any SMTP relay, including the SES SMTP interface
 * when API access isn't used. Requires {@code spring.mail.*} to be configured.
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "buddydrop.mail", name = "provider", havingValue = "smtp")
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final AppProperties.Mail mail;

    public SmtpEmailSender(JavaMailSender mailSender, AppProperties properties) {
        this.mailSender = mailSender;
        this.mail = properties.getMail();
    }

    @Override
    public void send(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setTo(to);
            helper.setFrom(mail.getFrom(), mail.getFromName());
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Sent email to {} via SMTP", to);
        } catch (jakarta.mail.MessagingException | UnsupportedEncodingException e) {
            log.error("SMTP could not send email to {}", to, e);
            throw new EmailDeliveryException("SMTP could not send the email", e);
        }
    }
}
