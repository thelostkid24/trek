package com.sahyatri.common.mail;

/** Sends one plain-text email. Feature code composes the message; this only delivers it. */
public interface MailTransport {

    void send(String to, String subject, String text);
}
