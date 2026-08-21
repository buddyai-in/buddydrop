package com.buddyai.buddydrop.mail;

import com.buddyai.buddydrop.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SesEmailSenderTest {

    @Test
    void buildsSesRequestWithFromDisplayNameRecipientSubjectAndHtml() {
        SesV2Client client = mock(SesV2Client.class);
        when(client.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder().messageId("msg-1").build());

        AppProperties props = new AppProperties();
        props.getMail().setProvider("ses");
        props.getMail().setFrom("no-reply@buddydrop.app");
        props.getMail().setFromName("BuddyDrop");
        props.getMail().setConfigurationSet("primary-set");

        new SesEmailSender(client, props).send("user@example.com", "Hello", "<p>hi</p>");

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(client).sendEmail(captor.capture());
        SendEmailRequest req = captor.getValue();

        assertThat(req.fromEmailAddress()).isEqualTo("BuddyDrop <no-reply@buddydrop.app>");
        assertThat(req.destination().toAddresses()).containsExactly("user@example.com");
        assertThat(req.content().simple().subject().data()).isEqualTo("Hello");
        assertThat(req.content().simple().body().html().data()).isEqualTo("<p>hi</p>");
        assertThat(req.configurationSetName()).isEqualTo("primary-set");
    }

    @Test
    void omitsConfigurationSetAndDisplayNameWhenNotConfigured() {
        SesV2Client client = mock(SesV2Client.class);
        when(client.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder().messageId("msg-2").build());

        AppProperties props = new AppProperties();
        props.getMail().setFrom("no-reply@buddydrop.app");
        props.getMail().setFromName("");   // no display name

        new SesEmailSender(client, props).send("to@example.com", "S", "<p>b</p>");

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(client).sendEmail(captor.capture());
        assertThat(captor.getValue().fromEmailAddress()).isEqualTo("no-reply@buddydrop.app");
        assertThat(captor.getValue().configurationSetName()).isNull();
    }
}
