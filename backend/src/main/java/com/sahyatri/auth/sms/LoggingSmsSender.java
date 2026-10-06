package com.sahyatri.auth.sms;

import com.sahyatri.common.config.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Dev sender (`SMS_PROVIDER=log`): prints the code instead of sending it. On a real HTTPS deployment
 * (`AUTH_COOKIE_SECURE=true`) the code is never logged — logs aren't a place for credentials.
 */
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "log", matchIfMissing = true)
public class LoggingSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsSender.class);

    private final boolean redact;

    public LoggingSmsSender(AuthProperties auth) {
        this.redact = auth.cookieSecure();
    }

    @Override
    public void sendOtp(String phone, String code) {
        if (redact) {
            log.warn("OTP SMS not sent: SMS_PROVIDER=log");
            return;
        }
        log.warn("DEV SMS — OTP for {} is {}", phone, code);
    }
}
