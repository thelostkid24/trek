package com.sahyatri.guide.dto;

import com.sahyatri.guide.entity.GuideDetails;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A guide's credentials. {@code yearsLeading} comes from {@code leadingSince} and today's year; null when unknown.
 * All fields are null for a guide nobody has filled in yet.
 */
public record GuideDetailsResponse(
        UUID guideId,
        Integer leadingSince,
        Integer yearsLeading,
        String languages,
        String certification,
        String certificationNumber,
        String quote) {

    public static GuideDetailsResponse of(UUID guideId, GuideDetails d, LocalDate today) {
        if (d == null) {
            return new GuideDetailsResponse(guideId, null, null, null, null, null, null);
        }
        Integer years = d.getLeadingSince() == null ? null : Math.max(0, today.getYear() - d.getLeadingSince());
        return new GuideDetailsResponse(guideId, d.getLeadingSince(), years, d.getLanguages(), d.getCertification(),
                d.getCertificationNumber(), d.getQuote());
    }
}
