package com.sahyatri.booking.service;

import com.sahyatri.booking.dto.EmailCodeResponse;
import com.sahyatri.booking.entity.BookingEmailCode;
import com.sahyatri.booking.repository.BookingEmailCodeRepository;
import com.sahyatri.common.config.AuthProperties;
import com.sahyatri.common.exception.ApiException;
import com.sahyatri.common.mail.MailTransport;
import com.sahyatri.common.util.HashingUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

/**
 * Proves a booking's contact email at checkout (§7.6): a 6-digit code goes to the address and comes back with the
 * hold. A correct code stays good until it expires, so a hold that fails for other reasons (seats just went) can be
 * retried without a new one.
 */
@Service
public class BookingEmailCodeService {

    static final Duration TTL = Duration.ofMinutes(10);
    static final Duration RESEND_AFTER = Duration.ofSeconds(30);
    static final int MAX_PER_HOUR = 5;
    static final int MAX_ATTEMPTS = 5;

    private final BookingEmailCodeRepository codes;
    private final MailTransport mail;
    private final String hmacKey;

    public BookingEmailCodeService(BookingEmailCodeRepository codes, MailTransport mail, AuthProperties props) {
        this.codes = codes;
        this.mail = mail;
        this.hmacKey = props.jwtSecret();
    }

    @Transactional
    public EmailCodeResponse send(String rawEmail) {
        String email = normalize(rawEmail);
        Instant now = Instant.now();
        codes.findFirstByEmailOrderByCreatedAtDesc(email).ifPresent(last -> {
            Instant allowedAt = last.getCreatedAt().plus(RESEND_AFTER);
            if (allowedAt.isAfter(now)) {
                throw rateLimited(Duration.between(now, allowedAt));
            }
        });
        Instant hourAgo = now.minus(Duration.ofHours(1));
        if (codes.countByEmailAndCreatedAtAfter(email, hourAgo) >= MAX_PER_HOUR) {
            Instant oldest = codes.findFirstByEmailAndCreatedAtAfterOrderByCreatedAtAsc(email, hourAgo)
                    .map(BookingEmailCode::getCreatedAt).orElse(now);
            throw rateLimited(Duration.between(now, oldest.plus(Duration.ofHours(1))));
        }

        String code = HashingUtils.randomDigits(6);
        codes.save(new BookingEmailCode(email, hash(email, code), now.plus(TTL)));
        mail.send(email, code + " is your The Empty Valley booking code", """
                Hi,

                Enter this code to confirm your email and book your trek with The Empty Valley:

                %s

                It works for 10 minutes. If you didn't ask for it, you can ignore this email.

                — The Empty Valley team""".formatted(code));
        return new EmailCodeResponse(TTL.toSeconds(), RESEND_AFTER.toSeconds());
    }

    /**
     * Checks the code against the latest one sent to this email, or throws. Runs in its own transaction so a wrong
     * code counts even though the booking that carried it fails.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
    public void check(String rawEmail, String code) {
        if (code == null || code.isBlank()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED",
                    "Confirm your email with the code we send to it");
        }
        String email = normalize(rawEmail);
        BookingEmailCode latest = codes.findFirstByEmailOrderByCreatedAtDesc(email)
                .orElseThrow(BookingEmailCodeService::expired);
        if (latest.getExpiresAt().isBefore(Instant.now())) {
            throw expired();
        }
        if (latest.getAttempts() >= MAX_ATTEMPTS) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "EMAIL_CODE_TOO_MANY_ATTEMPTS",
                    "Too many wrong codes, request a new one");
        }
        if (!HashingUtils.constantTimeEquals(latest.getCodeHash(), hash(email, code.trim()))) {
            latest.recordFailedAttempt();
            codes.save(latest);
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMAIL_CODE_INVALID", "Incorrect code",
                    Map.of("attempts_left", MAX_ATTEMPTS - latest.getAttempts()));
        }
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String hash(String email, String code) {
        return HashingUtils.hmacSha256Hex(hmacKey, "booking-email:" + email + ":" + code);
    }

    private static ApiException expired() {
        return new ApiException(HttpStatus.GONE, "EMAIL_CODE_EXPIRED", "Code expired, request a new one");
    }

    private static ApiException rateLimited(Duration wait) {
        long seconds = Math.max(1, (long) Math.ceil(wait.toMillis() / 1000.0));
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "EMAIL_CODE_RATE_LIMITED",
                "Please wait before requesting another code", Map.of("retry_after", seconds));
    }
}
