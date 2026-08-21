package com.buddyai.buddydrop.mail;

import com.buddyai.buddydrop.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.*;

/**
 * Sends email through the Amazon SES v2 API. Active when {@code buddydrop.mail.provider=ses}.
 *
 * <p>Reuses the same AWS credential chain and SDK style as S3, so no SMTP credentials are needed —
 * the app's IAM principal is granted {@code ses:SendEmail}. The from address must be a verified SES
 * identity (or the whole domain verified); in the SES sandbox recipients must be verified too.
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "buddydrop.mail", name = "provider", havingValue = "ses")
public class SesEmailSender implements EmailSender {

    private final SesV2Client ses;
    private final AppProperties.Mail mail;

    public SesEmailSender(SesV2Client ses, AppProperties properties) {
        this.ses = ses;
        this.mail = properties.getMail();
    }

    @Override
    public void send(String to, String subject, String htmlBody) {
        Content subjectContent = Content.builder().data(subject).charset("UTF-8").build();
        Content bodyContent = Content.builder().data(htmlBody).charset("UTF-8").build();

        SendEmailRequest.Builder request = SendEmailRequest.builder()
                .fromEmailAddress(fromAddress())
                .destination(Destination.builder().toAddresses(to).build())
                .content(EmailContent.builder()
                        .simple(Message.builder()
                                .subject(subjectContent)
                                .body(Body.builder().html(bodyContent).build())
                                .build())
                        .build());
        if (mail.getConfigurationSet() != null && !mail.getConfigurationSet().isBlank()) {
            request.configurationSetName(mail.getConfigurationSet());
        }

        try {
            SendEmailResponse response = ses.sendEmail(request.build());
            log.info("Sent email to {} via SES (messageId={})", to, response.messageId());
        } catch (SesV2Exception e) {
            log.error("SES rejected email to {}: {}", to, e.awsErrorDetails().errorMessage());
            throw new EmailDeliveryException("SES could not send the email", e);
        }
    }

    /** RFC 5322 from address, prefixed with the display name when one is configured. */
    private String fromAddress() {
        String from = mail.getFrom();
        String name = mail.getFromName();
        return (name == null || name.isBlank()) ? from : "%s <%s>".formatted(name, from);
    }
}
