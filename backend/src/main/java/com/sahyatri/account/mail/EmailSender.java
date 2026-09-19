package com.sahyatri.account.mail;

/** Delivers account emails. V1 dev implementation only logs; a real provider replaces it later. */
public interface EmailSender {

    void sendVerificationLink(String to, String link);

    /** Heads-up to the previous address after the account email was changed. */
    void sendEmailChangedNotice(String oldEmail, String newEmail);
}
