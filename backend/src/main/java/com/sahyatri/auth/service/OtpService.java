package com.sahyatri.auth.service;

import com.sahyatri.auth.dto.OtpRequestResponse;
import com.sahyatri.auth.entity.OtpChallenge;
import com.sahyatri.auth.entity.OtpPurpose;
import com.sahyatri.auth.repository.OtpChallengeRepository;
import com.sahyatri.auth.sms.SmsSender;
import com.sahyatri.common.config.AuthProperties;
import com.sahyatri.common.exception.ApiException;
import com.sahyatri.common.util.HashingUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** Issues and checks phone OTP codes. Knows nothing about accounts. */
@Service
public class OtpService {

    static final Duration TTL = Duration.ofMinutes(5);
    static final Duration RESEND_AFTER = Duration.ofSeconds(30);
    static final int MAX_PER_HOUR = 5;
    static final int MAX_ATTEMPTS = 5;

    private final OtpChallengeRepository challenges;
    private final SmsSender sms;
    private final String hmacKey;

    public OtpService(OtpChallengeRepository challenges, SmsSender sms, AuthProperties props) {
        this.challenges = challenges;
        this.sms = sms;
        this.hmacKey = props.jwtSecret();
    }

    @Transactional
    public OtpRequestResponse request(String phone, OtpPurpose purpose) {
        Instant now = Instant.now();
        challenges.findFirstByPhoneOrderByCreatedAtDesc(phone).ifPresent(last -> {
            Instant allowedAt = last.getCreatedAt().plus(RESEND_AFTER);
            if (allowedAt.isAfter(now)) {
                throw rateLimited(Duration.between(now, allowedAt));
            }
        });
        Instant hourAgo = now.minus(Duration.ofHours(1));
        if (challenges.countByPhoneAndCreatedAtAfter(phone, hourAgo) >= MAX_PER_HOUR) {
            Instant oldest = challenges.findFirstByPhoneAndCreatedAtAfterOrderByCreatedAtAsc(phone, hourAgo)
                    .map(OtpChallenge::getCreatedAt).orElse(now);
            throw rateLimited(Duration.between(now, oldest.plus(Duration.ofHours(1))));
        }

        String code = HashingUtils.randomDigits(6);
        challenges.save(new OtpChallenge(phone, purpose, hash(phone, code), now.plus(TTL)));
        sms.sendOtp(phone, code);
        return new OtpRequestResponse(TTL.toSeconds(), RESEND_AFTER.toSeconds());
    }

    /**
     * Consumes the latest code issued for this phone and purpose, or throws. Failed attempts are persisted
     * despite the exception.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public void verify(String phone, String code, OtpPurpose purpose) {
        OtpChallenge challenge = challenges.findFirstByPhoneAndPurposeOrderByCreatedAtDesc(phone, purpose)
                .filter(c -> c.getConsumedAt() == null)
                .orElseThrow(OtpService::expired);
        if (challenge.getExpiresAt().isBefore(Instant.now())) {
            throw expired();
        }
        if (challenge.getAttempts() >= MAX_ATTEMPTS) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_TOO_MANY_ATTEMPTS",
                    "Too many wrong codes, request a new one");
        }
        if (!HashingUtils.constantTimeEquals(challenge.getCodeHash(), hash(phone, code))) {
            challenge.recordFailedAttempt();
            int left = MAX_ATTEMPTS - challenge.getAttempts();
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP_INVALID", "Incorrect code",
                    Map.of("attempts_left", left));
        }
        challenge.consume();
    }

    private String hash(String phone, String code) {
        return HashingUtils.hmacSha256Hex(hmacKey, phone + ":" + code);
    }

    private static ApiException expired() {
        return new ApiException(HttpStatus.GONE, "OTP_EXPIRED", "Code expired, request a new one");
    }

    private static ApiException rateLimited(Duration wait) {
        long seconds = Math.max(1, (long) Math.ceil(wait.toMillis() / 1000.0));
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_RATE_LIMITED",
                "Please wait before requesting another code", Map.of("retry_after", seconds));
    }
}
