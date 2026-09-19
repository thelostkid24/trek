package com.sahyatri.auth.controller;

import com.sahyatri.auth.dto.AuthResponse;
import com.sahyatri.auth.dto.AuthSession;
import com.sahyatri.auth.dto.GoogleRequest;
import com.sahyatri.auth.dto.LoginRequest;
import com.sahyatri.auth.dto.OtpRequest;
import com.sahyatri.auth.dto.OtpRequestResponse;
import com.sahyatri.auth.dto.OtpVerifyRequest;
import com.sahyatri.auth.dto.SignupRequest;
import com.sahyatri.auth.dto.UserResponse;
import com.sahyatri.auth.entity.OtpPurpose;
import com.sahyatri.auth.service.AuthService;
import com.sahyatri.auth.service.OtpService;
import com.sahyatri.common.security.RefreshCookie;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Contract: docs/TRD.md §7.2. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final OtpService otp;
    private final RefreshCookie cookie;

    public AuthController(AuthService auth, OtpService otp, RefreshCookie cookie) {
        this.auth = auth;
        this.otp = otp;
        this.cookie = cookie;
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest req) {
        return withCookie(HttpStatus.CREATED, auth.signup(req));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return withCookie(HttpStatus.OK, auth.login(req));
    }

    @PostMapping("/otp/request")
    public ResponseEntity<OtpRequestResponse> requestOtp(@Valid @RequestBody OtpRequest req) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(otp.request(req.phone(), OtpPurpose.LOGIN));
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<AuthResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest req) {
        return withCookie(HttpStatus.OK, auth.verifyOtp(req));
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> google(@Valid @RequestBody GoogleRequest req) {
        return withCookie(HttpStatus.OK, auth.google(req));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@CookieValue(name = RefreshCookie.NAME, required = false) String token) {
        return withCookie(HttpStatus.OK, auth.refresh(token));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = RefreshCookie.NAME, required = false) String token) {
        auth.logout(token);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.clear()).build();
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return auth.me(UUID.fromString(jwt.getSubject()));
    }

    private ResponseEntity<AuthResponse> withCookie(HttpStatus status, AuthSession session) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie.set(session.refreshToken()))
                .body(session.body());
    }
}
