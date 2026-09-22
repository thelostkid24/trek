package com.sahyatri.account.mail;

import com.sahyatri.common.mail.MailTransport;
import org.springframework.stereotype.Component;

/** Composes account emails and hands them to the configured {@link MailTransport} (log in dev, SES in prod). */
@Component
public class DefaultEmailSender implements EmailSender {

    private final MailTransport mail;

    public DefaultEmailSender(MailTransport mail) {
        this.mail = mail;
    }

    @Override
    public void sendVerificationLink(String to, String link) {
        mail.send(to, "Verify your email for Sahyātri", """
                Hi,

                Confirm this email address for your Sahyātri account:
                %s

                If you didn't ask for this, you can ignore this email.

                — Team Sahyātri""".formatted(link));
    }

    @Override
    public void sendEmailChangedNotice(String oldEmail, String newEmail) {
        mail.send(oldEmail, "Your Sahyātri email was changed", """
                Hi,

                The email on your Sahyātri account was changed to %s.
                If this wasn't you, reply to this email right away.

                — Team Sahyātri""".formatted(newEmail));
    }
}
