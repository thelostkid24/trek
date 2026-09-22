package com.sahyatri.payment.entity;

import java.util.Locale;

public enum PaymentMethod {
    UPI, CARD, NETBANKING, WALLET;

    /** Razorpay's lowercase method name; methods we don't offer map to null. */
    public static PaymentMethod fromRazorpay(String method) {
        if (method == null) {
            return null;
        }
        try {
            return valueOf(method.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
