package com.sahyatri.payment.gateway;

import java.util.Map;

/**
 * A Razorpay payment, reduced to what we store.
 *
 * @param status       Razorpay status: created, authorized, captured, refunded, failed
 * @param methodDetail display-safe fields only (see {@link RazorpayJson#payment})
 */
public record GatewayPayment(
        String id,
        String orderId,
        String status,
        long amountPaise,
        String method,
        Map<String, String> methodDetail,
        String errorCode,
        String errorDescription) {

    public boolean captured() {
        // A captured payment that was later refunded is still money that reached us.
        return "captured".equals(status) || "refunded".equals(status);
    }
}
