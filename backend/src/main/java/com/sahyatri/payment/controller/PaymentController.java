package com.sahyatri.payment.controller;

import com.sahyatri.payment.dto.CreateOrderRequest;
import com.sahyatri.payment.dto.PaymentOrderResponse;
import com.sahyatri.payment.dto.PaymentResponse;
import com.sahyatri.payment.dto.VerifyPaymentRequest;
import com.sahyatri.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Contract: docs/TRD.md §7.4 (with §7.6 changes). TREKKER role enforced by SecurityConfig. */
@RestController
@RequestMapping("/api/trekker/payments")
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentOrderResponse createOrder(@AuthenticationPrincipal Jwt jwt,
                                            @Valid @RequestBody CreateOrderRequest req) {
        return payments.createOrder(userId(jwt), req.bookingId());
    }

    @PostMapping("/{id}/verify")
    public PaymentResponse verify(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                  @Valid @RequestBody VerifyPaymentRequest req) {
        return payments.verify(userId(jwt), id, req);
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return payments.get(userId(jwt), id);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
