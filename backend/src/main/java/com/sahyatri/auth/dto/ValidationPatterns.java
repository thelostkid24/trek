package com.sahyatri.auth.dto;

/** Regexes shared by request DTOs. Rules are documented in docs/TRD.md §7.2. */
public final class ValidationPatterns {

    private ValidationPatterns() {
    }

    public static final String PASSWORD = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";
    public static final String PASSWORD_MESSAGE = "must be 8-72 characters with a letter and a digit";

    public static final String INDIAN_MOBILE = "^\\+91[6-9]\\d{9}$";
    public static final String INDIAN_MOBILE_MESSAGE = "must be an Indian mobile number like +919876543210";

    public static final String OTP_CODE = "^\\d{6}$";
}
