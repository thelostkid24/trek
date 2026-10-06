package com.sahyatri.guide.dto;

import com.sahyatri.guide.entity.GuideDetails;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A guide's credentials. {@code yearsLeading} comes from {@code leadingSince} and today's year; null when unknown.
 * All fields are null for a guide nobody has filled in yet. {@code priorTreks}: treks they led before Sahyātri.
 */
public record GuideDetailsResponse(
        UUID guideId,
        Integer leadingSince,
        Integer yearsLeading,
        String languages,
        String certification,
        String certificationNumber,
        String bmcInstitute,
        String bmcCertificateNumber,
        String amcInstitute,
        String amcCertificateNumber,
        String quote,
        List<PriorTrek> priorTreks) {

    public static GuideDetailsResponse of(UUID guideId, GuideDetails d, List<PriorTrek> priorTreks, LocalDate today) {
        if (d == null) {
            return new GuideDetailsResponse(guideId, null, null, null, null, null, null, null, null, null, null,
                    priorTreks);
        }
        Integer years = d.getLeadingSince() == null ? null : Math.max(0, today.getYear() - d.getLeadingSince());
        return new GuideDetailsResponse(guideId, d.getLeadingSince(), years, d.getLanguages(), d.getCertification(),
                d.getCertificationNumber(), d.getBmcInstitute(), d.getBmcCertificateNumber(), d.getAmcInstitute(),
                d.getAmcCertificateNumber(), d.getQuote(), priorTreks);
    }
}
