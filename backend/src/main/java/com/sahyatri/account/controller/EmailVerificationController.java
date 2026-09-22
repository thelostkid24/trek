package com.sahyatri.account.controller;

import com.sahyatri.account.dto.EmailVerifyRequest;
import com.sahyatri.account.dto.EmailVerifyResponse;
import com.sahyatri.account.service.EmailChangeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Public: the link may be opened on a device where the user isn't signed in; the token is the proof. */
@RestController
public class EmailVerificationController {

    private final EmailChangeService email;

    public EmailVerificationController(EmailChangeService email) {
        this.email = email;
    }

    @PostMapping("/api/auth/email/verify")
    public EmailVerifyResponse verify(@Valid @RequestBody EmailVerifyRequest req) {
        return email.verify(req.token());
    }
}
