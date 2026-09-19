package com.sahyatri.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Razorpay (`app.payments`). Blank keys leave payments switched off: creating an order returns
 * GATEWAY_UNAVAILABLE. Only {@code keyId} is ever sent to browsers.
 */
@ConfigurationProperties("app.payments")
public record PaymentProperties(String keyId, String keySecret, String webhookSecret, String apiBaseUrl) {

    public boolean configured() {
        return notBlank(keyId) && notBlank(keySecret);
    }

    public boolean webhookConfigured() {
        return notBlank(webhookSecret);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
