package com.sahyatri.catalog.dto;

import com.sahyatri.guide.dto.Milestone;
import com.sahyatri.review.dto.PublicReview;

import java.util.List;
import java.util.UUID;

/**
 * Public guide page. Counts and the rating are computed at read time and never stored (law 8). {@code slug} is the
 * canonical URL part (null until assigned); the page replaces an old slug or an id in the address with it.
 * {@code highestAltitudeM}: the highest top among the treks they've led (with us or before), null when none is known.
 * {@code returnRatePct}: the share of trekkers from their completed departures who booked with them again, null under
 * {@link #RETURN_RATE_MIN_TREKKERS} trekkers (§7.21.2).
 */
public record GuideProfile(
        UUID id,
        String slug,
        String fullName,
        String avatarUrl,
        String coverUrl,
        String homeCity,
        String bio,
        long treksLed,
        Integer yearsLeading,
        String languages,
        String certification,
        String certificationNumber,
        String bmcInstitute,
        String bmcCertificateNumber,
        String amcInstitute,
        String amcCertificateNumber,
        String quote,
        Double rating,
        long reviewCount,
        Integer highestAltitudeM,
        Integer returnRatePct,
        List<PublicReview> reviews,
        List<String> specialties,
        List<Milestone> milestones,
        List<TrekLed> treks,
        List<DepartureSummary> upcoming) {

    public static final int RETURN_RATE_MIN_TREKKERS = 10;

    /** {@code note}: the guide's line about the route, or null. {@code maxAltitudeM} and {@code seasonLabel} are the trek's. */
    public record TrekLed(TrackBrief track, long times, Integer maxAltitudeM, String seasonLabel, String note) {
    }
}
