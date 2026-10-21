package com.sahyatri.question.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Questions never carry contact details between a trekker and a guide (§7.21.6): email addresses and phone numbers
 * typed into a message are replaced before it is stored, so neither the site nor the alert emails show them.
 */
final class ContactRedactor {

    static final String REMOVED = "[contact removed]";

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    // A run of digits with spaces, dots, dashes or brackets between them; it is a phone number at 10+ digits.
    private static final Pattern DIGITS = Pattern.compile("\\+?\\(?\\d[\\d\\s().-]*\\d");

    private ContactRedactor() {
    }

    static String redact(String text) {
        String noEmails = EMAIL.matcher(text).replaceAll(REMOVED);
        Matcher m = DIGITS.matcher(noEmails);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            long digits = m.group().chars().filter(Character::isDigit).count();
            m.appendReplacement(out, Matcher.quoteReplacement(digits >= 10 ? REMOVED : m.group()));
        }
        m.appendTail(out);
        return out.toString();
    }
}
