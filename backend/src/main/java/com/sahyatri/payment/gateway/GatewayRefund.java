package com.sahyatri.payment.gateway;

import java.util.Map;

/** @param status Razorpay status: pending, processed, failed */
public record GatewayRefund(String id, String paymentId, long amountPaise, String status, Map<String, String> notes) {
}
