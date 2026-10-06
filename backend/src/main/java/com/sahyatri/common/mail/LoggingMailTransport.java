package com.sahyatri.common.mail;

import com.sahyatri.common.config.AppProperties;
import com.sahyatri.common.config.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Dev transport (`MAIL_PROVIDER=log`): prints the message instead of sending it. On a real HTTPS deployment
 * (`AUTH_COOKIE_SECURE=true`) only the subject is logged — bodies carry sign-in and reset links.
 */
@Component
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "log", matchIfMissing = true)
public class LoggingMailTransport implements MailTransport {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailTransport.class);

    private final String from;
    private final boolean redact;

    public LoggingMailTransport(AppProperties props, AuthProperties auth) {
        this.from = props.mail().from();
        this.redact = auth.cookieSecure();
    }

    @Override
    public void send(String to, String subject, String text) {
        if (redact) {
            log.warn("Email not sent: MAIL_PROVIDER=log — {}", subject);
            return;
        }
        log.warn("DEV EMAIL from {} to {} — {}\n{}", from, to, subject, text);
    }
}
