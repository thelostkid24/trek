package com.sahyatri.payment.service;

import com.sahyatri.common.config.PaymentProperties;
import com.sahyatri.common.exception.ApiException;
import com.sahyatri.common.util.HashingUtils;
import com.sahyatri.payment.gateway.GatewayPayment;
import com.sahyatri.payment.gateway.RazorpayJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * Razorpay webhooks. The signature is checked on the raw bytes before parsing. Each event id is processed once:
 * the dedupe row and the processing share one transaction, so a failure rolls both back and Razorpay retries.
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final PaymentProperties props;
    private final PaymentService paymentService;
    private final RefundService refundService;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    public WebhookService(PaymentProperties props, PaymentService paymentService, RefundService refundService,
                          JdbcTemplate jdbc, ObjectMapper json, TransactionTemplate tx) {
        this.props = props;
        this.paymentService = paymentService;
        this.refundService = refundService;
        this.jdbc = jdbc;
        this.json = json;
        this.tx = tx;
    }

    public void handle(byte[] body, String signature, String eventId) {
        if (!props.webhookConfigured()) {
            log.warn("Razorpay webhook received but RAZORPAY_WEBHOOK_SECRET is not set");
            throw invalidSignature();
        }
        if (signature == null || !HashingUtils.constantTimeEquals(
                HashingUtils.hmacSha256Hex(props.webhookSecret(), body), signature)) {
            throw invalidSignature();
        }
        JsonNode root;
        try {
            root = json.readTree(body);
        } catch (JacksonException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is invalid");
        }
        String type = root.path("event").asString("");
        // Razorpay always sends the header; the body hash is a fallback so a missing header can't bypass dedupe.
        String id = eventId != null && !eventId.isBlank()
                ? eventId
                : "sha256:" + HashingUtils.sha256Hex(new String(body, StandardCharsets.UTF_8));

        tx.executeWithoutResult(status -> {
            int inserted = jdbc.update("""
                    INSERT INTO webhook_events (event_id, event_type, received_at) VALUES (?, ?, ?)
                    ON CONFLICT (event_id) DO NOTHING""", id, type, Timestamp.from(Instant.now()));
            if (inserted == 0) {
                log.info("Duplicate webhook {} ({}) ignored", id, type);
                return;
            }
            dispatch(type, root.path("payload"));
            jdbc.update("UPDATE webhook_events SET processed_at = ? WHERE event_id = ?",
                    Timestamp.from(Instant.now()), id);
        });
    }

    private void dispatch(String type, JsonNode payload) {
        switch (type) {
            case "payment.captured", "order.paid" -> {
                GatewayPayment remote = RazorpayJson.payment(payload.path("payment").path("entity"));
                if (!remote.captured()) {
                    return;
                }
                paymentService.findIdByOrderId(remote.orderId()).ifPresentOrElse(
                        paymentId -> paymentService.applyCaptured(paymentId, remote),
                        () -> log.info("Webhook for unknown order {} ignored", remote.orderId()));
            }
            case "payment.failed" ->
                    paymentService.recordFailure(RazorpayJson.payment(payload.path("payment").path("entity")));
            case "refund.processed", "refund.failed" ->
                    refundService.onGatewayRefund(RazorpayJson.refund(payload.path("refund").path("entity")));
            default -> log.debug("Webhook {} ignored", type);
        }
    }

    private static ApiException invalidSignature() {
        return new ApiException(HttpStatus.BAD_REQUEST, "WEBHOOK_SIGNATURE_INVALID", "Invalid webhook signature");
    }
}
