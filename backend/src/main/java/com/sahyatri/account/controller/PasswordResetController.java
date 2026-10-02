package com.sahyatri.account.controller;

import com.sahyatri.account.dto.PasswordForgotRequest;
import com.sahyatri.account.dto.PasswordForgotResponse;
import com.sahyatri.account.dto.PasswordResetRequest;
import com.sahyatri.account.service.PasswordResetService;
import com.sahyatri.auth.dto.AuthResponse;
import com.sahyatri.auth.dto.AuthSession;
import com.sahyatri.common.security.RefreshCookie;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Public: the user is signed out by definition; the emailed token is the proof. Contract: docs/TRD.md §7.2. */
@RestController
public class PasswordResetController {

    private final PasswordResetService resets;
    private final RefreshCookie cookie;

    public PasswordResetController(PasswordResetService resets, RefreshCookie cookie) {
        this.resets = resets;
        this.cookie = cookie;
    }

    @PostMapping("/api/auth/password/forgot")
    public ResponseEntity<PasswordForgotResponse> forgot(@Valid @RequestBody PasswordForgotRequest req) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(resets.request(req.email()));
    }

    @PostMapping("/api/auth/password/reset")
    public ResponseEntity<AuthResponse> reset(@Valid @RequestBody PasswordResetRequest req) {
        AuthSession session = resets.reset(req.token(), req.newPassword());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.set(session.refreshToken()))
                .body(session.body());
    }
}
