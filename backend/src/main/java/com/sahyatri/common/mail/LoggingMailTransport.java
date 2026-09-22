package com.sahyatri.common.mail;

import com.sahyatri.common.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Dev transport (`MAIL_PROVIDER=log`): prints the message instead of sending it. */
@Component
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "log", matchIfMissing = true)
public class LoggingMailTransport implements MailTransport {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailTransport.class);

    private final String from;

    public LoggingMailTransport(AppProperties props) {
        this.from = props.mail().from();
    }

    @Override
    public void send(String to, String subject, String text) {
        log.warn("DEV EMAIL from {} to {} — {}\n{}", from, to, subject, text);
    }
}
