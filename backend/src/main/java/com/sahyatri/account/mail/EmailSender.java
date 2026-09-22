package com.sahyatri.account.mail;

/** Account emails (verification, change notices). Delivered through MailTransport. */
public interface EmailSender {

    void sendVerificationLink(String to, String link);

    /** Heads-up to the previous address after the account email was changed. */
    void sendEmailChangedNotice(String oldEmail, String newEmail);
}
