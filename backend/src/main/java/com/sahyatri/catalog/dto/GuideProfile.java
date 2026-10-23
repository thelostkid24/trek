package com.sahyatri.catalog.dto;

import com.sahyatri.guide.dto.Milestone;
import com.sahyatri.guide.entity.GuideRouteKind;
import com.sahyatri.guide.entity.GuideStatKey;
import com.sahyatri.guide.entity.RouteAvailability;
import com.sahyatri.review.dto.PublicReview;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Public guide page. Counts and the rating are computed at read time and never stored (law 8). {@code slug} is the
 * canonical URL part (null until assigned); the page replaces an old slug or an id in the address with it.
 * {@code highestAltitudeM}: the highest top among the treks they've led (with us or before), null when none is known.
 * {@code returnRatePct}: the share of trekkers from their completed departures who booked with them again, null under
 * {@link #RETURN_RATE_MIN_TREKKERS} trekkers (§7.21.2). {@code stats}: the numbers an admin picked, worked out now;
 * null when none are picked (the page shows its default row). {@code trainedAt}: short names of the institutes of the
 * mountaineering courses on record ("NIM", "ABVIMAS"), basic first (§7.21.8). {@code certified}: a verified
 * certificate still valid today, the same rule as the credentials' {@code verified} (§7.21.3).
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
        List<DepartureSummary> upcoming,
        List<Stat> stats,
        List<String> trainedAt,
        boolean certified) {

    public static final int RETURN_RATE_MIN_TREKKERS = 10;

    /**
     * A route they lead (§7.21.8), in the order kept for them, then catalog treks completed with us that aren't on it.
     * {@code routeId} null for those. {@code track} null for a route we don't list; {@code name} is always set.
     * {@code times}: times led before Sahyātri plus completed departures with us; null when unknown ("Led").
     * {@code maxAltitudeM} and {@code seasonLabel} are the catalog trek's.
     */
    public record TrekLed(UUID routeId, TrackBrief track, String name, GuideRouteKind kind, Long times,
                          Integer lastLedYear, Integer lastLedMonth, boolean mostRecent, RouteAvailability availability,
                          Integer seasonFromMonth, Integer seasonToMonth, LocalDate availableFrom,
                          Integer maxAltitudeM, String seasonLabel, String note) {
    }

    /**
     * One number in the stats row: "50×" over "Kedarkantha led". {@code brief}: the short form the link card uses
     * ("Kedarkantha 50×", "21 treks"), null when the card doesn't use this one.
     */
    public record Stat(GuideStatKey key, String value, String label, String brief) {
    }
}
