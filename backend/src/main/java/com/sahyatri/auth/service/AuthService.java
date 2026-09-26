package com.sahyatri.auth.service;

import com.sahyatri.auth.dto.AuthResponse;
import com.sahyatri.auth.dto.AuthSession;
import com.sahyatri.auth.dto.GoogleIdentity;
import com.sahyatri.auth.dto.GoogleRequest;
import com.sahyatri.auth.dto.LoginRequest;
import com.sahyatri.auth.dto.OtpVerifyRequest;
import com.sahyatri.auth.dto.SignupRequest;
import com.sahyatri.auth.dto.UserResponse;
import com.sahyatri.auth.entity.OtpPurpose;
import com.sahyatri.auth.entity.SignupMethod;
import com.sahyatri.auth.entity.User;
import com.sahyatri.auth.repository.UserRepository;
import com.sahyatri.common.exception.ApiException;
import com.sahyatri.common.storage.AvatarFiles;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Account-level auth flows. Methods are deliberately not one big transaction: OTP attempt counts and
 * refresh-token reuse revocation must persist even when the flow ends in an error.
 */
@Service
public class AuthService {

    /** {@code last_seen_at} is written at most this often per user. */
    private static final Duration SEEN_EVERY = Duration.ofHours(1);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final OtpService otp;
    private final GoogleTokenVerifier google;
    private final LoginAttemptLimiter loginLimiter;
    private final CurrentUser currentUser;
    private final AvatarFiles avatars;
    private final AccountAcquisition acquisition;
    /** Compared against when the email is unknown so response time doesn't reveal account existence. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens, OtpService otp,
                       GoogleTokenVerifier google, LoginAttemptLimiter loginLimiter, CurrentUser currentUser,
                       AvatarFiles avatars, AccountAcquisition acquisition) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.otp = otp;
        this.google = google;
        this.loginLimiter = loginLimiter;
        this.currentUser = currentUser;
        this.avatars = avatars;
        this.acquisition = acquisition;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public AuthSession signup(SignupRequest req) {
        String email = normalizeEmail(req.email());
        if (users.existsByEmail(email)) {
            throw emailTaken();
        }
        User user = User.newTrekker();
        user.setFullName(req.fullName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        acquisition.apply(user, SignupMethod.EMAIL, req.acquisition());
        try {
            user = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw emailTaken();
        }
        acquisition.recordSignupConsent(user);
        return session(user, true);
    }

    public AuthSession login(LoginRequest req) {
        String email = normalizeEmail(req.email());
        if (loginLimiter.isBlocked(email)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Too many failed attempts, try again in a few minutes");
        }
        User user = users.findByEmail(email).orElse(null);
        String hash = user != null && user.getPasswordHash() != null ? user.getPasswordHash() : dummyHash;
        boolean matches = passwordEncoder.matches(req.password(), hash);
        if (user == null || user.getPasswordHash() == null || !matches) {
            loginLimiter.recordFailure(email);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect");
        }
        loginLimiter.reset(email);
        ensureActive(user);
        return session(user, false);
    }

    public AuthSession verifyOtp(OtpVerifyRequest req) {
        otp.verify(req.phone(), req.code(), OtpPurpose.LOGIN);
        User user = users.findByPhone(req.phone()).orElse(null);
        boolean isNew = user == null;
        if (isNew) {
            user = User.newTrekker();
            user.setPhone(req.phone());
            acquisition.apply(user, SignupMethod.PHONE, req.acquisition());
        }
        ensureActive(user);
        if (user.getPhoneVerifiedAt() == null) {
            user.setPhoneVerifiedAt(Instant.now());
        }
        if (user.getFullName() == null && req.fullName() != null && !req.fullName().isBlank()) {
            user.setFullName(req.fullName().trim());
        }
        return newOrExisting(users.save(user), isNew);
    }

    public AuthSession google(GoogleRequest req) {
        GoogleIdentity identity = google.verify(req.idToken());
        String email = identity.email() != null ? normalizeEmail(identity.email()) : null;

        User user = users.findByGoogleSubject(identity.subject()).orElse(null);
        boolean isNew = false;
        if (user == null && identity.emailVerified() && email != null) {
            // Link Google to an existing account with the same (Google-verified) email.
            user = users.findByEmail(email).orElse(null);
        }
        if (user == null) {
            isNew = true;
            user = User.newTrekker();
            user.setFullName(identity.name());
            if (identity.emailVerified() && email != null) {
                user.setEmail(email);
            }
            acquisition.apply(user, SignupMethod.GOOGLE, req.acquisition());
        }
        ensureActive(user);
        user.setGoogleSubject(identity.subject());
        if (identity.emailVerified() && email != null && email.equals(user.getEmail()) && user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(Instant.now());
        }
        return newOrExisting(users.save(user), isNew);
    }

    public AuthSession refresh(String refreshToken) {
        TokenService.Rotation rotation = tokens.rotate(refreshToken);
        User user = users.findById(rotation.userId()).orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID", "Session expired, please sign in again"));
        if (user.isDisabled()) {
            tokens.revokeAll(user.getId());
            throw disabled();
        }
        markSeen(user);
        return new AuthSession(authResponse(user, tokens.accessToken(user), false), rotation.refreshToken());
    }

    public void logout(String refreshToken) {
        tokens.revoke(refreshToken);
    }

    public UserResponse me(UUID userId) {
        return toResponse(currentUser.require(userId));
    }

    public UserResponse toResponse(User user) {
        return UserResponse.of(user, avatars);
    }

    /** Fresh access + refresh token for an existing user, e.g. after a password change revoked the old ones. */
    public AuthSession newSession(User user) {
        return session(user, false);
    }

    /** First session of an account created outside the auth flows, e.g. a guest at checkout. */
    public AuthSession firstSession(User user) {
        return session(user, true);
    }

    private AuthSession newOrExisting(User user, boolean isNew) {
        if (isNew) {
            acquisition.recordSignupConsent(user);
        }
        return session(user, isNew);
    }

    private AuthSession session(User user, boolean isNew) {
        markSeen(user);
        TokenService.IssuedTokens issued = tokens.issue(user);
        return new AuthSession(authResponse(user, issued.accessToken(), isNew), issued.refreshToken());
    }

    private AuthResponse authResponse(User user, String accessToken, boolean isNew) {
        return new AuthResponse(accessToken, "Bearer", tokens.accessTtlSeconds(), isNew, toResponse(user));
    }

    private void markSeen(User user) {
        Instant now = Instant.now();
        users.markSeen(user.getId(), now, now.minus(SEEN_EVERY));
    }

    private static void ensureActive(User user) {
        if (user.isDisabled()) {
            throw disabled();
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static ApiException disabled() {
        return new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "This account has been disabled");
    }

    private static ApiException emailTaken() {
        return ApiException.conflict("EMAIL_ALREADY_REGISTERED", "An account with this email already exists");
    }
}
