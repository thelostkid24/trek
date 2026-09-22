package com.sahyatri.account.service;

import com.sahyatri.account.dto.EmailChangeResponse;
import com.sahyatri.account.dto.EmailVerifyResponse;
import com.sahyatri.account.entity.EmailVerification;
import com.sahyatri.account.mail.EmailSender;
import com.sahyatri.account.repository.EmailVerificationRepository;
import com.sahyatri.auth.entity.User;
import com.sahyatri.auth.repository.UserRepository;
import com.sahyatri.auth.service.CurrentUser;
import com.sahyatri.common.config.AppProperties;
import com.sahyatri.common.exception.ApiException;
import com.sahyatri.common.util.HashingUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Verifies the current email or switches to a new one. The account only changes once the link sent to the
 * target address is opened, so nobody can claim an address they can't read.
 */
@Service
public class EmailChangeService {

    static final Duration TTL = Duration.ofHours(24);
    static final int MAX_PER_HOUR = 5;

    private final EmailVerificationRepository verifications;
    private final UserRepository users;
    private final CurrentUser currentUser;
    private final EmailSender mail;
    private final String frontendBaseUrl;

    public EmailChangeService(EmailVerificationRepository verifications, UserRepository users, CurrentUser currentUser,
                              EmailSender mail, AppProperties props) {
        this.verifications = verifications;
        this.users = users;
        this.currentUser = currentUser;
        this.mail = mail;
        this.frontendBaseUrl = props.frontendBaseUrl().replaceAll("/+$", "");
    }

    public EmailChangeResponse request(UUID userId, String rawEmail) {
        User user = currentUser.require(userId);
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        boolean isCurrent = email.equals(user.getEmail());
        if (isCurrent && user.getEmailVerifiedAt() != null) {
            throw ApiException.conflict("EMAIL_ALREADY_VERIFIED", "This email is already verified");
        }
        if (!isCurrent && users.existsByEmail(email)) {
            throw emailTaken();
        }

        Instant now = Instant.now();
        Instant hourAgo = now.minus(Duration.ofHours(1));
        if (verifications.countByUserIdAndCreatedAtAfter(userId, hourAgo) >= MAX_PER_HOUR) {
            Instant oldest = verifications.findFirstByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(userId, hourAgo)
                    .map(EmailVerification::getCreatedAt).orElse(now);
            long seconds = Math.max(1, Duration.between(now, oldest.plus(Duration.ofHours(1))).toSeconds());
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "EMAIL_RATE_LIMITED",
                    "Too many emails requested, try again later", Map.of("retry_after", seconds));
        }

        String token = HashingUtils.randomUrlToken(32);
        verifications.save(new EmailVerification(userId, email, HashingUtils.sha256Hex(token), now.plus(TTL)));
        mail.sendVerificationLink(email, frontendBaseUrl + "/account/verify-email?token=" + token);
        return new EmailChangeResponse(TTL.toSeconds());
    }

    /** Not one transaction: a unique-email violation must surface as 409, not poison the whole unit of work. */
    public EmailVerifyResponse verify(String token) {
        EmailVerification verification = verifications.findByTokenHash(HashingUtils.sha256Hex(token))
                .filter(v -> v.getConsumedAt() == null)
                .orElseThrow(EmailChangeService::invalidToken);
        boolean superseded = verifications.findFirstByUserIdOrderByCreatedAtDesc(verification.getUserId())
                .map(latest -> !latest.getId().equals(verification.getId()))
                .orElse(true);
        if (superseded) {
            throw invalidToken();
        }
        if (verification.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.GONE, "EMAIL_TOKEN_EXPIRED", "This link has expired, request a new one");
        }
        User user = users.findById(verification.getUserId())
                .filter(u -> !u.isDisabled())
                .orElseThrow(EmailChangeService::invalidToken);

        String previous = user.getEmail();
        String email = verification.getEmail();
        boolean changed = !email.equals(previous);
        if (changed && users.existsByEmail(email)) {
            throw emailTaken();
        }
        user.setEmail(email);
        user.setEmailVerifiedAt(Instant.now());
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw emailTaken();
        }
        verification.consume();
        verifications.save(verification);

        if (changed && previous != null) {
            mail.sendEmailChangedNotice(previous, email);
        }
        return new EmailVerifyResponse(email);
    }

    private static ApiException invalidToken() {
        return new ApiException(HttpStatus.BAD_REQUEST, "EMAIL_TOKEN_INVALID", "This link is invalid or was already used");
    }

    private static ApiException emailTaken() {
        return ApiException.conflict("EMAIL_ALREADY_REGISTERED", "An account with this email already exists");
    }
}
