package com.sahyatri.auth.dto;

public record OtpRequestResponse(long expiresIn, long resendAfter) {
}
