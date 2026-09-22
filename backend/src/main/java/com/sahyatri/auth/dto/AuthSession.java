package com.sahyatri.auth.dto;

/** Service result: the response body plus the raw refresh token the controller puts in the cookie. */
public record AuthSession(AuthResponse body, String refreshToken) {
}
