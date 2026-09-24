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
        mail.send(to, "Verify your email for The Empty Valley", """
                Hi,

                Confirm this email address for your account with The Empty Valley:
                %s

                If you didn't ask for this, you can ignore this email.

                — The Empty Valley team""".formatted(link));
    }

    @Override
    public void sendEmailChangedNotice(String oldEmail, String newEmail) {
        mail.send(oldEmail, "Your email for The Empty Valley was changed", """
                Hi,

                The email on your account with The Empty Valley was changed to %s.
                If this wasn't you, reply to this email right away.

                — The Empty Valley team""".formatted(newEmail));
    }
}
