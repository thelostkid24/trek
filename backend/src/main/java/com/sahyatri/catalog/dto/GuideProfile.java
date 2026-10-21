package com.sahyatri.catalog.dto;

import com.sahyatri.guide.dto.Milestone;
import com.sahyatri.review.dto.PublicReview;

import java.util.List;
import java.util.UUID;

/**
 * Public guide page. Counts and the rating are computed at read time and never stored (law 8). {@code slug} is the
 * canonical URL part (null until assigned); the page replaces an old slug or an id in the address with it.
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
        List<PublicReview> reviews,
        List<String> specialties,
        List<Milestone> milestones,
        List<TrekLed> treks,
        List<DepartureSummary> upcoming) {

    /** {@code note}: the guide's line about the route, or null. */
    public record TrekLed(TrackBrief track, long times, String note) {
    }
}
