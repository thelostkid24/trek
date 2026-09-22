package com.sahyatri.payment;

import com.sahyatri.payment.gateway.GatewayException;
import com.sahyatri.payment.gateway.GatewayPayment;
import com.sahyatri.payment.gateway.GatewayRefund;
import com.sahyatri.payment.gateway.PaymentGateway;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** In-memory Razorpay for tests. Tests drive it with {@link #capture}, {@link #fail} and {@link #failRefunds}. */
public class FakePaymentGateway implements PaymentGateway {

    public static final String KEY_ID = "rzp_test_fake";
    public static final String KEY_SECRET = "fake-key-secret";

    public record Order(String id, long amountPaise, String receipt, Map<String, String> notes) {
    }

    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final Map<String, GatewayPayment> payments = new ConcurrentHashMap<>();
    private final Map<String, List<GatewayRefund>> refunds = new ConcurrentHashMap<>();
    private final AtomicInteger refundFailures = new AtomicInteger();

    @TestConfiguration
    public static class Config {
        @Bean
        @Primary
        public FakePaymentGateway fakePaymentGateway() {
            return new FakePaymentGateway();
        }
    }

    @Override
    public String keyId() {
        return KEY_ID;
    }

    @Override
    public String checkoutSigningKey() {
        return KEY_SECRET;
    }

    @Override
    public String createOrder(long amountPaise, String currency, String receipt, Map<String, String> notes) {
        String id = "order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        orders.put(id, new Order(id, amountPaise, receipt, notes));
        return id;
    }

    @Override
    public Optional<GatewayPayment> fetchPayment(String paymentId) {
        return Optional.ofNullable(payments.get(paymentId));
    }

    @Override
    public List<GatewayPayment> fetchOrderPayments(String orderId) {
        return payments.values().stream().filter(p -> p.orderId().equals(orderId)).toList();
    }

    @Override
    public GatewayRefund createRefund(String paymentId, long amountPaise, Map<String, String> notes) {
        if (refundFailures.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
            throw new GatewayException("fake refund failure");
        }
        GatewayRefund refund = new GatewayRefund("rfnd_" + UUID.randomUUID().toString().substring(0, 12), paymentId,
                amountPaise, "pending", notes);
        refunds.computeIfAbsent(paymentId, k -> new CopyOnWriteArrayList<>()).add(refund);
        return refund;
    }

    @Override
    public List<GatewayRefund> fetchPaymentRefunds(String paymentId) {
        return new ArrayList<>(refunds.getOrDefault(paymentId, List.of()));
    }

    public Order order(String orderId) {
        return orders.get(orderId);
    }

    /** A successful card payment on the order, for its full amount. */
    public GatewayPayment capture(String orderId) {
        return capture(orderId, orders.get(orderId).amountPaise());
    }

    public GatewayPayment capture(String orderId, long amountPaise) {
        GatewayPayment payment = new GatewayPayment(newPaymentId(), orderId, "captured", amountPaise, "card",
                Map.of("card_network", "RuPay", "card_last4", "4242"), null, null);
        payments.put(payment.id(), payment);
        return payment;
    }

    /** Authorised but not captured yet (as if auto-capture hasn't run). */
    public GatewayPayment authorize(String orderId) {
        GatewayPayment payment = new GatewayPayment(newPaymentId(), orderId, "authorized",
                orders.get(orderId).amountPaise(), "upi", Map.of("vpa", "as***@okhdfcbank"), null, null);
        payments.put(payment.id(), payment);
        return payment;
    }

    public GatewayPayment fail(String orderId) {
        GatewayPayment payment = new GatewayPayment(newPaymentId(), orderId, "failed",
                orders.get(orderId).amountPaise(), "card", Map.of(), "BAD_REQUEST_ERROR", "Card declined");
        payments.put(payment.id(), payment);
        return payment;
    }

    public List<GatewayRefund> refundsFor(String paymentId) {
        return fetchPaymentRefunds(paymentId);
    }

    /** The next {@code count} createRefund calls throw. */
    public void failRefunds(int count) {
        refundFailures.set(count);
    }

    private static String newPaymentId() {
        return "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
    }
}
