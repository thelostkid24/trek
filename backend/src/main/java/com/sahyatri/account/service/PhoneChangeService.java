package com.sahyatri.account.service;

import com.sahyatri.auth.dto.OtpRequestResponse;
import com.sahyatri.auth.dto.UserResponse;
import com.sahyatri.auth.entity.OtpPurpose;
import com.sahyatri.auth.entity.User;
import com.sahyatri.auth.repository.UserRepository;
import com.sahyatri.auth.service.AuthService;
import com.sahyatri.auth.service.CurrentUser;
import com.sahyatri.auth.service.OtpService;
import com.sahyatri.common.exception.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/** Adds or replaces the account phone once the caller proves they receive SMS on it. */
@Service
public class PhoneChangeService {

    private final UserRepository users;
    private final CurrentUser currentUser;
    private final OtpService otp;
    private final AuthService auth;

    public PhoneChangeService(UserRepository users, CurrentUser currentUser, OtpService otp, AuthService auth) {
        this.users = users;
        this.currentUser = currentUser;
        this.otp = otp;
        this.auth = auth;
    }

    public OtpRequestResponse requestCode(UUID userId, String phone) {
        User user = currentUser.require(userId);
        if (phone.equals(user.getPhone()) && user.getPhoneVerifiedAt() != null) {
            throw ApiException.conflict("PHONE_ALREADY_VERIFIED", "This number is already verified on your account");
        }
        ensureAvailable(phone, userId);
        return otp.request(phone, OtpPurpose.PHONE_CHANGE);
    }

    public UserResponse verify(UUID userId, String phone, String code) {
        User user = currentUser.require(userId);
        ensureAvailable(phone, userId);
        otp.verify(phone, code, OtpPurpose.PHONE_CHANGE);
        user.setPhone(phone);
        user.setPhoneVerifiedAt(Instant.now());
        try {
            user = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw phoneTaken();
        }
        return auth.toResponse(user);
    }

    private void ensureAvailable(String phone, UUID userId) {
        users.findByPhone(phone)
                .filter(owner -> !owner.getId().equals(userId))
                .ifPresent(owner -> {
                    throw phoneTaken();
                });
    }

    private static ApiException phoneTaken() {
        return ApiException.conflict("PHONE_ALREADY_REGISTERED", "Another account already uses this number");
    }
}
