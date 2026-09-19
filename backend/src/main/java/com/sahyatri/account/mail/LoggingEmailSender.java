package com.sahyatri.account.mail;

import com.sahyatri.common.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    private final String from;

    public LoggingEmailSender(AppProperties props) {
        this.from = props.mail().from();
    }

    @Override
    public void sendVerificationLink(String to, String link) {
        log.warn("DEV EMAIL from {} to {} — verify your email: {}", from, to, link);
    }

    @Override
    public void sendEmailChangedNotice(String oldEmail, String newEmail) {
        log.warn("DEV EMAIL from {} to {} — your Sahyātri email was changed to {}", from, oldEmail, newEmail);
    }
}
