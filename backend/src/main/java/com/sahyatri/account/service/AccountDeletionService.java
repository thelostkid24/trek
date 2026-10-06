package com.sahyatri.account.service;

import com.sahyatri.account.dto.AccountDeleteRequest;
import com.sahyatri.auth.entity.Role;
import com.sahyatri.auth.entity.User;
import com.sahyatri.auth.repository.UserRepository;
import com.sahyatri.auth.service.CurrentUser;
import com.sahyatri.auth.service.LoginAttemptLimiter;
import com.sahyatri.auth.service.TokenService;
import com.sahyatri.common.audit.AuditLog;
import com.sahyatri.common.config.CatalogProperties;
import com.sahyatri.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * A trekker deletes their own account (docs/TRD.md §7.18). Everything that identifies them is erased at once; the
 * user row, bookings, payments, reviews and audit events stay, because tax and accounting rules need the money records
 * for 8 years and other rows point at them. Not while a paid trek is still ahead (law 1: that booking stands), and
 * not for guides or admins, whose accounts carry departures and payouts: our team closes those.
 */
@Service
public class AccountDeletionService {

    static final String ACTION = "ACCOUNT_DELETED";
    /** Stands in for erased traveller names; the column is NOT NULL. */
    static final String REMOVED = "Removed";

    private final UserRepository users;
    private final CurrentUser currentUser;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptLimiter attempts;
    private final TokenService tokens;
    private final AvatarService avatars;
    private final AuditLog audit;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final CatalogProperties catalog;

    public AccountDeletionService(UserRepository users, CurrentUser currentUser, PasswordEncoder passwordEncoder,
                                  LoginAttemptLimiter attempts, TokenService tokens, AvatarService avatars,
                                  AuditLog audit, JdbcTemplate jdbc, TransactionTemplate tx, CatalogProperties catalog) {
        this.users = users;
        this.currentUser = currentUser;
        this.passwordEncoder = passwordEncoder;
        this.attempts = attempts;
        this.tokens = tokens;
        this.avatars = avatars;
        this.audit = audit;
        this.jdbc = jdbc;
        this.tx = tx;
        this.catalog = catalog;
    }

    public void delete(UUID userId, AccountDeleteRequest req) {
        String avatarKey = tx.execute(status -> erase(userId, req == null ? null : req.password()));
        // The row no longer points at the photo, so it goes after the commit.
        avatars.deleteQuietly(avatarKey);
    }

    /** Returns the avatar key the account had, for the caller to delete once committed. */
    private String erase(UUID userId, String password) {
        User user = currentUser.require(userId);
        // A booking insert locks this row too (its foreign key), so one in flight commits first and the check sees it.
        jdbc.queryForObject("SELECT id FROM users WHERE id = ? FOR UPDATE", UUID.class, userId);
        if (user.getRole() != Role.TREKKER) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_DELETION_UNAVAILABLE",
                    "Guide and admin accounts are closed by our team. Write to us and we'll do it.");
        }
        if (user.getPasswordHash() != null) {
            checkPassword(user, password);
        }
        LocalDate today = LocalDate.now(catalog.zone());
        Integer upcoming = jdbc.queryForObject("""
                SELECT count(*) FROM bookings b JOIN departures d ON d.id = b.departure_id
                WHERE b.user_id = ? AND b.status IN ('HELD', 'CONFIRMED') AND d.end_date >= ?""",
                Integer.class, userId, today);
        if (upcoming != null && upcoming > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "UPCOMING_TRIP",
                    "You have a trek coming up. You can delete your account once it's over, or after cancelling it.");
        }

        String avatarKey = user.getAvatarKey();
        String phone = user.getPhone();
        jdbc.update("DELETE FROM trekker_profiles WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM email_verifications WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM password_resets WHERE user_id = ?", userId);
        if (phone != null) {
            jdbc.update("DELETE FROM otp_challenges WHERE phone = ?", phone);
        }
        int bookings = jdbc.update("""
                UPDATE bookings SET contact_name = NULL, contact_phone = NULL, contact_email = NULL,
                    gclid = NULL, fbclid = NULL, referrer = NULL, landing_path = NULL
                WHERE user_id = ?""", userId);
        jdbc.update("""
                UPDATE booking_travellers t SET full_name = ?, phone = NULL
                FROM bookings b WHERE b.id = t.booking_id AND b.user_id = ?""", REMOVED, userId);
        tokens.revokeAll(userId);

        Instant now = Instant.now();
        user.erase(now);
        users.save(user);
        audit.record(userId, ACTION, "USER", userId, Map.of("bookings_kept", bookings));
        return avatarKey;
    }

    private void checkPassword(User user, String password) {
        String key = "account-delete:" + user.getId();
        if (attempts.isBlocked(key)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Too many failed attempts, try again in a few minutes");
        }
        if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            attempts.recordFailure(key);
            throw new ApiException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_INCORRECT", "Password is incorrect");
        }
        attempts.reset(key);
    }
}
