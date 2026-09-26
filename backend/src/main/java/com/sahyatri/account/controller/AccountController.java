package com.sahyatri.account.controller;

import com.sahyatri.account.dto.EmailChangeRequest;
import com.sahyatri.account.dto.EmailChangeResponse;
import com.sahyatri.account.dto.MarketingConsentRequest;
import com.sahyatri.account.dto.PasswordChangeRequest;
import com.sahyatri.account.dto.PhoneVerifyRequest;
import com.sahyatri.account.service.AvatarService;
import com.sahyatri.account.service.EmailChangeService;
import com.sahyatri.account.service.MarketingConsentService;
import com.sahyatri.account.service.PasswordService;
import com.sahyatri.account.service.PhoneChangeService;
import com.sahyatri.auth.dto.AuthResponse;
import com.sahyatri.auth.dto.AuthSession;
import com.sahyatri.auth.dto.OtpRequest;
import com.sahyatri.auth.dto.OtpRequestResponse;
import com.sahyatri.auth.dto.UserResponse;
import com.sahyatri.common.security.RefreshCookie;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/** The signed-in user's own account, any role. Contract: docs/TRD.md §7.3. */
@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AvatarService avatars;
    private final EmailChangeService email;
    private final PhoneChangeService phone;
    private final PasswordService password;
    private final MarketingConsentService consent;
    private final RefreshCookie cookie;

    public AccountController(AvatarService avatars, EmailChangeService email, PhoneChangeService phone,
                             PasswordService password, MarketingConsentService consent, RefreshCookie cookie) {
        this.avatars = avatars;
        this.email = email;
        this.phone = phone;
        this.password = password;
        this.consent = consent;
        this.cookie = cookie;
    }

    @PutMapping(path = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserResponse uploadAvatar(@AuthenticationPrincipal Jwt jwt, @RequestPart("file") MultipartFile file) {
        return avatars.upload(userId(jwt), file);
    }

    @DeleteMapping("/avatar")
    public UserResponse removeAvatar(@AuthenticationPrincipal Jwt jwt) {
        return avatars.remove(userId(jwt));
    }

    @PostMapping("/email")
    public ResponseEntity<EmailChangeResponse> changeEmail(@AuthenticationPrincipal Jwt jwt,
                                                           @Valid @RequestBody EmailChangeRequest req) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(email.request(userId(jwt), req.email()));
    }

    @PostMapping("/phone/otp")
    public ResponseEntity<OtpRequestResponse> requestPhoneCode(@AuthenticationPrincipal Jwt jwt,
                                                               @Valid @RequestBody OtpRequest req) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(phone.requestCode(userId(jwt), req.phone()));
    }

    @PostMapping("/phone/verify")
    public UserResponse verifyPhone(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PhoneVerifyRequest req) {
        return phone.verify(userId(jwt), req.phone(), req.code());
    }

    @PutMapping("/password")
    public ResponseEntity<AuthResponse> changePassword(@AuthenticationPrincipal Jwt jwt,
                                                       @Valid @RequestBody PasswordChangeRequest req) {
        AuthSession session = password.change(userId(jwt), req);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.set(session.refreshToken()))
                .body(session.body());
    }

    @PatchMapping("/marketing-consent")
    public UserResponse updateMarketingConsent(@AuthenticationPrincipal Jwt jwt,
                                               @RequestBody MarketingConsentRequest req) {
        return consent.update(userId(jwt), req);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
