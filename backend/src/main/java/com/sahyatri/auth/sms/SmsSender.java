package com.sahyatri.auth.sms;

/** Delivers OTP codes: logged in dev, MSG91 in prod (`app.sms.provider`). */
public interface SmsSender {

    void sendOtp(String phone, String code);
}
