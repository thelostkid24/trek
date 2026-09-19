package com.sahyatri.auth.dto;

/** Body returned by signup, login, otp/verify, google and refresh. */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        boolean isNewUser,
        UserResponse user) {
}
