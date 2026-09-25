package com.sahyatri.auth.service;

import com.sahyatri.auth.entity.SignupMethod;
import com.sahyatri.auth.entity.User;
import com.sahyatri.common.acquisition.AcquisitionRequest;
import com.sahyatri.common.acquisition.HeardFrom;
import com.sahyatri.common.acquisition.Touch;
import com.sahyatri.common.audit.AuditLog;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Where a new account came from (§7.15): first touch, sign-up method, heard-from and marketing consent. Applied
 * only to accounts being created; an existing account signing in keeps what it has.
 */
@Component
public class AccountAcquisition {

    public static final String CONSENT_GRANTED = "MARKETING_CONSENT_GRANTED";
    public static final String CONSENT_WITHDRAWN = "MARKETING_CONSENT_WITHDRAWN";

    private final AuditLog audit;

    public AccountAcquisition(AuditLog audit) {
        this.audit = audit;
    }

    /** Call before the first save. {@code req} may be null (an older client, or nothing captured). */
    public void apply(User user, SignupMethod method, AcquisitionRequest req) {
        if (req == null) {
            user.setAcquisition(method, null, null, null);
            return;
        }
        HeardFrom heardFrom = req.heardFrom();
        String note = heardFrom == null || req.heardFromNote() == null || req.heardFromNote().isBlank()
                ? null : req.heardFromNote().trim();
        user.setAcquisition(method, Touch.from(req.firstOrLast(), req.device()), heardFrom, note);
        Instant now = Instant.now();
        if (Boolean.TRUE.equals(req.marketingEmail())) user.setMarketingEmailConsentAt(now);
        if (Boolean.TRUE.equals(req.marketingWhatsapp())) user.setMarketingWhatsappConsentAt(now);
    }

    /** Call after the first save: the audit row references the user. Records consent given at sign-up. */
    public void recordSignupConsent(User user) {
        if (user.getMarketingEmailConsentAt() != null) consentChanged(user, "EMAIL", true, "SIGNUP");
        if (user.getMarketingWhatsappConsentAt() != null) consentChanged(user, "WHATSAPP", true, "SIGNUP");
    }

    /** Proof of consent under the DPDP Act: who, which channel, when, and where it was changed. */
    public void consentChanged(User user, String channel, boolean granted, String via) {
        audit.record(user.getId(), granted ? CONSENT_GRANTED : CONSENT_WITHDRAWN, "USER", user.getId(),
                Map.of("channel", channel, "via", via));
    }
}
