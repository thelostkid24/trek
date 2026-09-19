package com.sahyatri.auth.sms;

/** Delivers OTP codes. V1 dev implementation only logs; a real provider replaces it later. */
public interface SmsSender {

    void sendOtp(String phone, String code);
}
