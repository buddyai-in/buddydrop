package com.buddyai.buddydrop.mail;

/**
 * Transport seam for outgoing email. {@link MailService} owns the message content (subject, HTML
 * body) and delegates delivery to whichever implementation is wired in — SES, SMTP, or a dev logger —
 * selected by {@code buddydrop.mail.provider}. This mirrors the {@code StorageService} abstraction:
 * business code depends on the interface, not a concrete provider.
 */
public interface EmailSender {

    /**
     * Deliver an HTML email.
     *
     * @throws EmailDeliveryException if the message could not be handed off to the transport
     */
    void send(String to, String subject, String htmlBody);
}
