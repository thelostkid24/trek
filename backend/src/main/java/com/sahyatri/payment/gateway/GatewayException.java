package com.sahyatri.payment.gateway;

import com.sahyatri.common.exception.ApiException;
import org.springframework.http.HttpStatus;

/** Razorpay unreachable, misconfigured or refusing the call. Rendered as 502 GATEWAY_UNAVAILABLE. */
public class GatewayException extends ApiException {

    public GatewayException(String detail) {
        super(HttpStatus.BAD_GATEWAY, "GATEWAY_UNAVAILABLE", "The payment provider is unavailable. Please try again.");
        initCause(new RuntimeException(detail));
    }
}
