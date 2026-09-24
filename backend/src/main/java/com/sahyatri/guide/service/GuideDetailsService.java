package com.sahyatri.guide.service;

import com.sahyatri.auth.entity.Role;
import com.sahyatri.auth.repository.UserRepository;
import com.sahyatri.common.config.CatalogProperties;
import com.sahyatri.common.exception.ApiException;
import com.sahyatri.guide.dto.GuideDetailsRequest;
import com.sahyatri.guide.dto.GuideDetailsResponse;
import com.sahyatri.guide.entity.GuideDetails;
import com.sahyatri.guide.repository.GuideDetailsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Guide credentials (§7.12): an admin fills them in; the trek, departure and guide pages show them. */
@Service
public class GuideDetailsService {

    private final GuideDetailsRepository details;
    private final UserRepository users;
    private final CatalogProperties catalog;

    public GuideDetailsService(GuideDetailsRepository details, UserRepository users, CatalogProperties catalog) {
        this.details = details;
        this.users = users;
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public GuideDetailsResponse get(UUID guideId) {
        requireGuide(guideId);
        return GuideDetailsResponse.of(guideId, details.findById(guideId).orElse(null), today());
    }

    @Transactional
    public GuideDetailsResponse update(UUID guideId, GuideDetailsRequest req) {
        requireGuide(guideId);
        if (req.leadingSince() != null && req.leadingSince() > today().getYear()) {
            throw ApiException.validation("leading_since", "can't be in the future");
        }
        GuideDetails d = details.findById(guideId).orElseGet(() -> new GuideDetails(guideId));
        d.update(req.leadingSince(), blankToNull(req.languages()), blankToNull(req.certification()),
                blankToNull(req.certificationNumber()), blankToNull(req.quote()));
        return GuideDetailsResponse.of(guideId, details.saveAndFlush(d), today());
    }

    /** Credentials for several guides; a guide with none gets an all-null entry. */
    @Transactional(readOnly = true)
    public Map<UUID, GuideDetailsResponse> forGuides(Collection<UUID> guideIds) {
        Map<UUID, GuideDetailsResponse> byGuide = new HashMap<>();
        details.findAllById(guideIds).forEach(d -> byGuide.put(d.getUserId(),
                GuideDetailsResponse.of(d.getUserId(), d, today())));
        guideIds.forEach(id -> byGuide.putIfAbsent(id, GuideDetailsResponse.of(id, null, today())));
        return byGuide;
    }

    private void requireGuide(UUID guideId) {
        users.findById(guideId)
                .filter(u -> u.getRole() == Role.GUIDE)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GUIDE_NOT_FOUND", "Guide not found"));
    }

    private LocalDate today() {
        return LocalDate.now(catalog.zone());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
