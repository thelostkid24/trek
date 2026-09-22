package com.sahyatri.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OTP delivery (`app.sms.*`).
 *
 * @param provider      {@code log} (dev: code printed to the log) or {@code msg91}
 * @param msg91AuthKey  MSG91 auth key
 * @param msg91OtpTemplateId MSG91 Flow template id of the DLT-approved OTP template; it must use the variable ##otp##
 */
@ConfigurationProperties("app.sms")
public record SmsProperties(String provider, String msg91AuthKey, String msg91OtpTemplateId) {
}
