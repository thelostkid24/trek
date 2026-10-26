package com.sahyatri.guide.dto;

import com.sahyatri.guide.entity.GuideDetails;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A guide's credentials. {@code yearsLeading} comes from {@code leadingSince} and today's year; null when unknown.
 * All fields are null for a guide nobody has filled in yet. {@code priorTreks}: treks they led before Sahyātri.
 * {@code slug}: the profile URL's last part, null until assigned (§7.21). {@code routeVerified}: admin-set (§7.17).
 */
public record GuideDetailsResponse(
        UUID guideId,
        String slug,
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
        boolean routeVerified,
        List<PriorTrek> priorTreks) {

    public static GuideDetailsResponse of(UUID guideId, GuideDetails d, List<PriorTrek> priorTreks, LocalDate today) {
        if (d == null) {
            return new GuideDetailsResponse(guideId, null, null, null, null, null, null, null, null, null, null, null,
                    false, priorTreks);
        }
        Integer years = d.getLeadingSince() == null ? null : Math.max(0, today.getYear() - d.getLeadingSince());
        return new GuideDetailsResponse(guideId, d.getSlug(), d.getLeadingSince(), years, d.getLanguages(), d.getCertification(),
                d.getCertificationNumber(), d.getBmcInstitute(), d.getBmcCertificateNumber(), d.getAmcInstitute(),
                d.getAmcCertificateNumber(), d.getQuote(), d.isRouteVerified(), priorTreks);
    }
}
