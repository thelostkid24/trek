package com.sahyatri.catalog.dto;

import com.sahyatri.review.dto.PublicReview;

import java.util.List;
import java.util.UUID;

/**
 * Public guide page. Counts and the rating are computed at read time and never stored (law 8).
 */
public record GuideProfile(
        UUID id,
        String fullName,
        String avatarUrl,
        String homeCity,
        String bio,
        long treksLed,
        Integer yearsLeading,
        String languages,
        String certification,
        String certificationNumber,
        String quote,
        Double rating,
        long reviewCount,
        List<PublicReview> reviews,
        List<TrekLed> treks,
        List<DepartureSummary> upcoming) {

    public record TrekLed(TrackBrief track, long times) {
    }
}
