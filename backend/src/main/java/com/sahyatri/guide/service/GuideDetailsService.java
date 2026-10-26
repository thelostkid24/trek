package com.sahyatri.guide.service;

import com.sahyatri.auth.entity.Role;
import com.sahyatri.auth.repository.UserRepository;
import com.sahyatri.common.config.CatalogProperties;
import com.sahyatri.common.exception.ApiException;
import com.sahyatri.guide.dto.GuideDetailsRequest;
import com.sahyatri.guide.dto.GuideDetailsResponse;
import com.sahyatri.guide.dto.PriorTrek;
import com.sahyatri.guide.entity.GuideDetails;
import com.sahyatri.guide.repository.GuideDetailsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Guide credentials (§7.12): an admin fills them in; the trek, departure and guide pages show them, with how often the
 * guide led each trek before Sahyātri from their routes (§7.21.8).
 */
@Service
public class GuideDetailsService {

    private final GuideDetailsRepository details;
    private final GuideRouteService routes;
    private final UserRepository users;
    private final CatalogProperties catalog;

    public GuideDetailsService(GuideDetailsRepository details, GuideRouteService routes, UserRepository users,
                               CatalogProperties catalog) {
        this.details = details;
        this.routes = routes;
        this.users = users;
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public GuideDetailsResponse get(UUID guideId) {
        requireGuide(guideId);
        return GuideDetailsResponse.of(guideId, details.findById(guideId).orElse(null), priorFor(guideId), today());
    }

    @Transactional
    public GuideDetailsResponse update(UUID guideId, GuideDetailsRequest req) {
        requireGuide(guideId);
        if (req.leadingSince() != null && req.leadingSince() > today().getYear()) {
            throw ApiException.validation("leading_since", "can't be in the future");
        }
        GuideDetails d = details.findById(guideId).orElseGet(() -> new GuideDetails(guideId));
        d.update(req.leadingSince(), blankToNull(req.languages()), blankToNull(req.certification()),
                blankToNull(req.certificationNumber()), blankToNull(req.bmcInstitute()),
                blankToNull(req.bmcCertificateNumber()), blankToNull(req.amcInstitute()),
                blankToNull(req.amcCertificateNumber()), blankToNull(req.quote()), Boolean.TRUE.equals(req.routeVerified()),
                req.batchesLed());
        return GuideDetailsResponse.of(guideId, details.saveAndFlush(d), priorFor(guideId), today());
    }

    private List<PriorTrek> priorFor(UUID guideId) {
        return routes.priorTreks(Set.of(guideId)).getOrDefault(guideId, List.of()).stream()
                .sorted(Comparator.comparingInt(PriorTrek::times).reversed())
                .toList();
    }

    /** Credentials for several guides; a guide with none gets an all-null entry. */
    @Transactional(readOnly = true)
    public Map<UUID, GuideDetailsResponse> forGuides(Collection<UUID> guideIds) {
        Map<UUID, GuideDetailsResponse> byGuide = new HashMap<>();
        Map<UUID, List<PriorTrek>> prior = routes.priorTreks(guideIds);
        details.findAllById(guideIds).forEach(d -> byGuide.put(d.getUserId(),
                GuideDetailsResponse.of(d.getUserId(), d, prior.getOrDefault(d.getUserId(), List.of()), today())));
        guideIds.forEach(id -> byGuide.putIfAbsent(id,
                GuideDetailsResponse.of(id, null, prior.getOrDefault(id, List.of()), today())));
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
