package com.sahyatri.payment.gateway;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Razorpay operations we use. {@code RazorpayGateway} in the app; a fake in tests. All failures throw {@link GatewayException}. */
public interface PaymentGateway {

    /** Public key id sent to Checkout. */
    String keyId();

    /** Secret used for the Checkout order|payment signature. */
    String checkoutSigningKey();

    /** @return the Razorpay order id */
    String createOrder(long amountPaise, String currency, String receipt, Map<String, String> notes);

    Optional<GatewayPayment> fetchPayment(String paymentId);

    List<GatewayPayment> fetchOrderPayments(String orderId);

    GatewayRefund createRefund(String paymentId, long amountPaise, Map<String, String> notes);

    List<GatewayRefund> fetchPaymentRefunds(String paymentId);
}
