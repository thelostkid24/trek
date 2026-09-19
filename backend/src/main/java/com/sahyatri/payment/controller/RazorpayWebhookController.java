package com.sahyatri.payment.controller;

import com.sahyatri.payment.service.WebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** No JWT: the X-Razorpay-Signature HMAC over the raw body is the authentication (§7.4). */
@RestController
public class RazorpayWebhookController {

    private final WebhookService webhooks;

    public RazorpayWebhookController(WebhookService webhooks) {
        this.webhooks = webhooks;
    }

    @PostMapping("/api/webhooks/razorpay")
    public ResponseEntity<Void> receive(@RequestBody byte[] body,
                                        @RequestHeader(name = "X-Razorpay-Signature", required = false) String signature,
                                        @RequestHeader(name = "X-Razorpay-Event-Id", required = false) String eventId) {
        webhooks.handle(body, signature, eventId);
        return ResponseEntity.ok().build();
    }
}
