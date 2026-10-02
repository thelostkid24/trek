package com.sahyatri.account.dto;

/** Same body whether or not the email has an account, so the endpoint can't be used to probe for accounts. */
public record PasswordForgotResponse(long expiresIn) {
}
