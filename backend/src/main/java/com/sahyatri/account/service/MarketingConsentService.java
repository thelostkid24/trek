package com.sahyatri.account.service;

import com.sahyatri.account.dto.MarketingConsentRequest;
import com.sahyatri.auth.dto.UserResponse;
import com.sahyatri.auth.entity.User;
import com.sahyatri.auth.repository.UserRepository;
import com.sahyatri.auth.service.AccountAcquisition;
import com.sahyatri.auth.service.AuthService;
import com.sahyatri.auth.service.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Opt in or out of trek offers by email and WhatsApp (§7.15). Every actual change is audited. */
@Service
public class MarketingConsentService {

    private final UserRepository users;
    private final CurrentUser currentUser;
    private final AccountAcquisition acquisition;
    private final AuthService auth;

    public MarketingConsentService(UserRepository users, CurrentUser currentUser, AccountAcquisition acquisition,
                                   AuthService auth) {
        this.users = users;
        this.currentUser = currentUser;
        this.acquisition = acquisition;
        this.auth = auth;
    }

    @Transactional
    public UserResponse update(UUID userId, MarketingConsentRequest req) {
        User user = currentUser.require(userId);
        Instant now = Instant.now();
        boolean hadEmail = user.getMarketingEmailConsentAt() != null;
        if (req.email() != null && req.email() != hadEmail) {
            user.setMarketingEmailConsentAt(req.email() ? now : null);
            acquisition.consentChanged(user, "EMAIL", req.email(), "ACCOUNT");
        }
        boolean hadWhatsapp = user.getMarketingWhatsappConsentAt() != null;
        if (req.whatsapp() != null && req.whatsapp() != hadWhatsapp) {
            user.setMarketingWhatsappConsentAt(req.whatsapp() ? now : null);
            acquisition.consentChanged(user, "WHATSAPP", req.whatsapp(), "ACCOUNT");
        }
        return auth.toResponse(users.save(user));
    }
}
