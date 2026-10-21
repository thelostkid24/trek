package com.sahyatri.question.service;

import com.sahyatri.common.config.AuthProperties;
import com.sahyatri.common.exception.ApiException;
import com.sahyatri.common.util.HashingUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Signed, expiring links that let a visitor read and answer their thread without an account (§7.21.6). A token is
 * {@code <thread id>.<expiry epoch seconds>.<HMAC-SHA256 hex>}, keyed by {@code JWT_SECRET} under its own prefix so
 * it can't be swapped for any other signed value. Nothing is stored: each email we send carries a fresh link.
 */
@Component
public class QuestionLinks {

    static final Duration TTL = Duration.ofDays(30);

    private final String key;

    public QuestionLinks(AuthProperties props) {
        this.key = props.jwtSecret();
    }

    public String issue(UUID threadId) {
        return issue(threadId, Instant.now().plus(TTL));
    }

    public String issue(UUID threadId, Instant expiresAt) {
        long exp = expiresAt.getEpochSecond();
        return threadId + "." + exp + "." + sign(threadId, exp);
    }

    /** The thread a token names, or 403 QUESTION_LINK_INVALID / 410 QUESTION_LINK_EXPIRED. */
    public UUID verify(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.", -1);
        if (parts.length != 3) {
            throw invalid();
        }
        UUID threadId;
        long exp;
        try {
            threadId = UUID.fromString(parts[0]);
            exp = Long.parseLong(parts[1]);
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
        // Signature first, so an edited expiry reads as tampering, not as expired.
        if (!threadId.toString().equals(parts[0]) || !HashingUtils.constantTimeEquals(sign(threadId, exp), parts[2])) {
            throw invalid();
        }
        if (Instant.now().getEpochSecond() >= exp) {
            throw new ApiException(HttpStatus.GONE, "QUESTION_LINK_EXPIRED",
                    "This link has expired. Open the newest email about your question for a fresh one.");
        }
        return threadId;
    }

    private String sign(UUID threadId, long exp) {
        return HashingUtils.hmacSha256Hex(key, "question-link:" + threadId + ":" + exp);
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.FORBIDDEN, "QUESTION_LINK_INVALID", "This link isn't valid");
    }
}
