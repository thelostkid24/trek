package com.sahyatri.guide.service;

import com.sahyatri.auth.entity.Role;
import com.sahyatri.auth.repository.UserRepository;
import com.sahyatri.catalog.repository.TrackRepository;
import com.sahyatri.common.config.CatalogProperties;
import com.sahyatri.common.exception.ApiException;
import com.sahyatri.guide.dto.GuideDetailsRequest;
import com.sahyatri.guide.dto.GuideDetailsResponse;
import com.sahyatri.guide.dto.PriorTrek;
import com.sahyatri.guide.entity.GuideDetails;
import com.sahyatri.guide.entity.GuidePriorTrek;
import com.sahyatri.guide.repository.GuideDetailsRepository;
import com.sahyatri.guide.repository.GuidePriorTrekRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Guide credentials (§7.12): an admin fills them in, with how often the guide led each trek before Sahyātri; the trek,
 * departure and guide pages show them.
 */
@Service
public class GuideDetailsService {

    private final GuideDetailsRepository details;
    private final GuidePriorTrekRepository priorTreks;
    private final UserRepository users;
    private final TrackRepository tracks;
    private final CatalogProperties catalog;

    public GuideDetailsService(GuideDetailsRepository details, GuidePriorTrekRepository priorTreks,
                               UserRepository users, TrackRepository tracks, CatalogProperties catalog) {
        this.details = details;
        this.priorTreks = priorTreks;
        this.users = users;
        this.tracks = tracks;
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
        if (req.priorTreks() != null) {
            replacePriorTreks(guideId, req.priorTreks());
        }
        GuideDetails d = details.findById(guideId).orElseGet(() -> new GuideDetails(guideId));
        d.update(req.leadingSince(), blankToNull(req.languages()), blankToNull(req.certification()),
                blankToNull(req.certificationNumber()), blankToNull(req.bmcInstitute()),
                blankToNull(req.bmcCertificateNumber()), blankToNull(req.amcInstitute()),
                blankToNull(req.amcCertificateNumber()), blankToNull(req.quote()));
        return GuideDetailsResponse.of(guideId, details.saveAndFlush(d), priorFor(guideId), today());
    }

    private void replacePriorTreks(UUID guideId, List<PriorTrek> list) {
        Set<UUID> seen = new HashSet<>();
        for (PriorTrek p : list) {
            if (!seen.add(p.trackId())) {
                throw ApiException.validation("prior_treks", "lists a trek twice");
            }
        }
        if (tracks.findAllById(seen).size() != seen.size()) {
            throw ApiException.validation("prior_treks", "names a trek that doesn't exist");
        }
        priorTreks.deleteForGuide(guideId);
        priorTreks.saveAllAndFlush(list.stream().map(p -> new GuidePriorTrek(guideId, p.trackId(), p.times())).toList());
    }

    private List<PriorTrek> priorFor(UUID guideId) {
        return priorTreks.findByGuideIdIn(Set.of(guideId)).stream()
                .sorted(Comparator.comparingInt(GuidePriorTrek::getTimes).reversed())
                .map(p -> new PriorTrek(p.getTrackId(), p.getTimes()))
                .toList();
    }

    /** Credentials for several guides; a guide with none gets an all-null entry. */
    @Transactional(readOnly = true)
    public Map<UUID, GuideDetailsResponse> forGuides(Collection<UUID> guideIds) {
        Map<UUID, GuideDetailsResponse> byGuide = new HashMap<>();
        Map<UUID, List<PriorTrek>> prior = priorTreks.findByGuideIdIn(guideIds).stream().collect(Collectors.groupingBy(
                GuidePriorTrek::getGuideId, Collectors.mapping(p -> new PriorTrek(p.getTrackId(), p.getTimes()),
                        Collectors.toList())));
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
