package com.sahyatri.payment.service;

import com.sahyatri.common.audit.AuditLog;
import com.sahyatri.payment.entity.Payment;
import com.sahyatri.payment.entity.PaymentRefund;
import com.sahyatri.payment.entity.RefundKind;
import com.sahyatri.payment.entity.RefundStatus;
import com.sahyatri.payment.gateway.GatewayRefund;
import com.sahyatri.payment.gateway.PaymentGateway;
import com.sahyatri.payment.repository.PaymentRefundRepository;
import com.sahyatri.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Refunds in three steps: reserve (a PENDING row, in the caller's transaction, which caps
 * amount_refunded_paise), submit to Razorpay after commit, then finish from the webhook or the reconciler.
 */
@Service
public class RefundService {

    static final String NOTE_REFUND_ID = "refund_id";
    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final PaymentRefundRepository refunds;
    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final ApplicationEventPublisher events;
    private final AuditLog audit;
    private final TransactionTemplate newTx;

    public RefundService(PaymentRefundRepository refunds, PaymentRepository payments, PaymentGateway gateway,
                         ApplicationEventPublisher events, AuditLog audit, PlatformTransactionManager txManager) {
        this.refunds = refunds;
        this.payments = payments;
        this.gateway = gateway;
        this.events = events;
        this.audit = audit;
        this.newTx = new TransactionTemplate(txManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Reserves a refund on a PAID payment the caller has locked. The amount is capped at what is left.
     *
     * @return the refund row, or empty when nothing is left to refund
     */
    public Optional<PaymentRefund> request(Payment payment, long paise, RefundKind kind, String reason) {
        long amount = Math.min(paise, payment.refundablePaise());
        if (amount <= 0) {
            return Optional.empty();
        }
        payment.reserveRefund(amount);
        payments.save(payment);
        PaymentRefund refund = refunds.save(PaymentRefund.pending(payment.getId(), amount, kind, reason));
        events.publishEvent(new RefundRequestedEvent(refund.getId()));
        return Optional.of(refund);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onRequested(RefundRequestedEvent event) {
        try {
            submit(event.refundId(), false);
        } catch (RuntimeException e) {
            // Left PENDING; the reconciler retries.
            log.warn("Refund {} not submitted yet: {}", event.refundId(), e.getMessage());
        }
    }

    /**
     * Sends a PENDING refund to Razorpay in its own transaction. On a retry, a refund Razorpay already holds
     * for this row (matched by notes.refund_id) is adopted instead of refunding twice.
     */
    public void submit(UUID refundId, boolean retry) {
        newTx.executeWithoutResult(status -> {
            PaymentRefund refund = refunds.findByIdForUpdate(refundId).orElseThrow();
            if (refund.getStatus() != RefundStatus.PENDING || refund.getRazorpayRefundId() != null) {
                return;
            }
            String razorpayPaymentId = payments.findById(refund.getPaymentId()).orElseThrow().getRazorpayPaymentId();
            GatewayRefund accepted = (retry ? gateway.fetchPaymentRefunds(razorpayPaymentId).stream()
                    .filter(r -> refundId.toString().equals(r.notes().get(NOTE_REFUND_ID)))
                    .findFirst() : Optional.<GatewayRefund>empty())
                    .orElseGet(() -> gateway.createRefund(razorpayPaymentId, refund.getAmountPaise(),
                            Map.of(NOTE_REFUND_ID, refundId.toString(), "kind", refund.getKind().name())));
            refund.accepted(accepted.id());
            refunds.save(refund);
            if ("processed".equals(accepted.status())) {
                refund.processed();
            }
            log.info("Refund {} accepted by Razorpay as {}", refundId, accepted.id());
        });
    }

    /** refund.processed / refund.failed webhooks. Joins the caller's transaction. */
    public void onGatewayRefund(GatewayRefund update) {
        Optional<PaymentRefund> found = refunds.findByRazorpayRefundId(update.id())
                .or(() -> Optional.ofNullable(update.notes().get(NOTE_REFUND_ID))
                        .flatMap(RefundService::parseUuid)
                        .flatMap(refunds::findById));
        if (found.isEmpty()) {
            log.info("Ignoring Razorpay refund {} we didn't create", update.id());
            return;
        }
        // Lock order: payment, then refund.
        Payment payment = payments.findByIdForUpdate(found.get().getPaymentId()).orElseThrow();
        PaymentRefund refund = refunds.findByIdForUpdate(found.get().getId()).orElseThrow();
        if (refund.getStatus() != RefundStatus.PENDING) {
            return;
        }
        if (refund.getRazorpayRefundId() == null) {
            refund.accepted(update.id());
        }
        switch (update.status()) {
            case "processed" -> refund.processed();
            case "failed" -> {
                refund.failed();
                payment.releaseRefund(refund.getAmountPaise());
                audit.record(null, "REFUND_FAILED", "PAYMENT", payment.getId(),
                        Map.of("refund_id", refund.getId().toString(), "amount_paise", refund.getAmountPaise()));
                log.error("Refund {} failed at Razorpay; needs follow-up", refund.getId());
            }
            default -> {
            }
        }
    }

    private static Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
