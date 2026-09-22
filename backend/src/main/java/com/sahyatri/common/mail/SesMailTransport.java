package com.sahyatri.common.mail;

import com.sahyatri.common.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sesv2.SesV2Client;

/**
 * Amazon SES (`MAIL_PROVIDER=ses`). The sender domain must be verified in SES and the account out of the
 * sandbox. Credentials and region come from the ECS task (default chain).
 */
@Component
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "ses")
public class SesMailTransport implements MailTransport {

    private final SesV2Client ses = SesV2Client.create();
    private final String from;

    public SesMailTransport(AppProperties props) {
        this.from = props.mail().from();
    }

    @Override
    public void send(String to, String subject, String text) {
        ses.sendEmail(b -> b
                .fromEmailAddress(from)
                .destination(d -> d.toAddresses(to))
                .content(c -> c.simple(m -> m
                        .subject(s -> s.data(subject).charset("UTF-8"))
                        .body(body -> body.text(t -> t.data(text).charset("UTF-8"))))));
    }
}
