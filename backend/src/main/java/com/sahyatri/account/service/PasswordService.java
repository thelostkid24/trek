package com.sahyatri.account.service;

import com.sahyatri.account.dto.PasswordChangeRequest;
import com.sahyatri.auth.dto.AuthSession;
import com.sahyatri.auth.entity.User;
import com.sahyatri.auth.repository.UserRepository;
import com.sahyatri.auth.service.AuthService;
import com.sahyatri.auth.service.CurrentUser;
import com.sahyatri.auth.service.LoginAttemptLimiter;
import com.sahyatri.auth.service.TokenService;
import com.sahyatri.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Sets a first password or changes an existing one. Either way every other session is signed out. */
@Service
public class PasswordService {

    private final UserRepository users;
    private final CurrentUser currentUser;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptLimiter attempts;
    private final TokenService tokens;
    private final AuthService auth;

    public PasswordService(UserRepository users, CurrentUser currentUser, PasswordEncoder passwordEncoder,
                           LoginAttemptLimiter attempts, TokenService tokens, AuthService auth) {
        this.users = users;
        this.currentUser = currentUser;
        this.passwordEncoder = passwordEncoder;
        this.attempts = attempts;
        this.tokens = tokens;
        this.auth = auth;
    }

    public AuthSession change(UUID userId, PasswordChangeRequest req) {
        User user = currentUser.require(userId);
        if (user.getEmail() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMAIL_REQUIRED",
                    "Add an email first — password sign-in uses your email");
        }
        if (user.getPasswordHash() != null) {
            checkCurrentPassword(user, req.currentPassword());
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user = users.save(user);
        tokens.revokeAll(userId);
        return auth.newSession(user);
    }

    private void checkCurrentPassword(User user, String currentPassword) {
        String key = "password-change:" + user.getId();
        if (attempts.isBlocked(key)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Too many failed attempts, try again in a few minutes");
        }
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            attempts.recordFailure(key);
            throw new ApiException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_INCORRECT", "Current password is incorrect");
        }
        attempts.reset(key);
    }
}
