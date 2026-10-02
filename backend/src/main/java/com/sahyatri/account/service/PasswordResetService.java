package com.sahyatri.account.service;

import com.sahyatri.account.dto.PasswordForgotResponse;
import com.sahyatri.account.entity.PasswordReset;
import com.sahyatri.account.mail.EmailSender;
import com.sahyatri.account.repository.PasswordResetRepository;
import com.sahyatri.auth.dto.AuthSession;
import com.sahyatri.auth.entity.User;
import com.sahyatri.auth.repository.UserRepository;
import com.sahyatri.auth.service.AuthService;
import com.sahyatri.auth.service.LoginAttemptLimiter;
import com.sahyatri.auth.service.TokenService;
import com.sahyatri.common.config.AppProperties;
import com.sahyatri.common.exception.ApiException;
import com.sahyatri.common.util.HashingUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * Forgot password: emails a single-use link, and opening it sets a new password and signs the user in.
 * Requests answer the same way whether or not the email has an account.
 */
@Service
public class PasswordResetService {

    static final Duration TTL = Duration.ofMinutes(30);
    static final int MAX_PER_HOUR = 3;

    private final PasswordResetRepository resets;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptLimiter loginLimiter;
    private final TokenService tokens;
    private final AuthService auth;
    private final EmailSender mail;
    private final String frontendBaseUrl;

    public PasswordResetService(PasswordResetRepository resets, UserRepository users, PasswordEncoder passwordEncoder,
                                LoginAttemptLimiter loginLimiter, TokenService tokens, AuthService auth,
                                EmailSender mail, AppProperties props) {
        this.resets = resets;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.loginLimiter = loginLimiter;
        this.tokens = tokens;
        this.auth = auth;
        this.mail = mail;
        this.frontendBaseUrl = props.frontendBaseUrl().replaceAll("/+$", "");
    }

    /** Unknown, disabled and over-the-limit requests are silently dropped: the caller always sees the same answer. */
    public PasswordForgotResponse request(String rawEmail) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        users.findByEmail(email)
                .filter(u -> !u.isDisabled())
                .filter(u -> resets.countByUserIdAndCreatedAtAfter(u.getId(), Instant.now().minus(Duration.ofHours(1)))
                        < MAX_PER_HOUR)
                .ifPresent(user -> {
                    String token = HashingUtils.randomUrlToken(32);
                    resets.save(new PasswordReset(user.getId(), email, HashingUtils.sha256Hex(token),
                            Instant.now().plus(TTL)));
                    mail.sendPasswordResetLink(email, frontendBaseUrl + "/reset-password?token=" + token);
                });
        return new PasswordForgotResponse(TTL.toSeconds());
    }

    /** Sets the new password, signs out every other session and returns a fresh one. */
    @Transactional
    public AuthSession reset(String token, String newPassword) {
        PasswordReset reset = resets.findByTokenHash(HashingUtils.sha256Hex(token))
                .filter(r -> r.getConsumedAt() == null)
                .orElseThrow(PasswordResetService::invalidToken);
        boolean superseded = resets.findFirstByUserIdOrderByCreatedAtDesc(reset.getUserId())
                .map(latest -> !latest.getId().equals(reset.getId()))
                .orElse(true);
        if (superseded) {
            throw invalidToken();
        }
        if (reset.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.GONE, "PASSWORD_RESET_EXPIRED", "This link has expired, request a new one");
        }
        User user = users.findById(reset.getUserId())
                .filter(u -> !u.isDisabled())
                .filter(u -> Objects.equals(u.getEmail(), reset.getEmail()))
                .orElseThrow(PasswordResetService::invalidToken);

        reset.consume();
        resets.save(reset);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        // Opening the link proves the address is theirs.
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(Instant.now());
        }
        user = users.save(user);
        tokens.revokeAll(user.getId());
        loginLimiter.reset(reset.getEmail());
        return auth.newSession(user);
    }

    private static ApiException invalidToken() {
        return new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_RESET_INVALID", "This link is invalid or was already used");
    }
}
