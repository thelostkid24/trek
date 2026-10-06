package com.sahyatri.catalog.dto;

import java.util.List;
import java.util.UUID;

/**
 * One guide on the public guides list (§7.17): who they are, their credentials, record and rating, the treks they've
 * led (most often first) and how many upcoming dates they lead. Counts and the rating are computed at read time (law 8).
 */
public record GuideListItem(
        UUID id,
        String fullName,
        String avatarUrl,
        String homeCity,
        Integer yearsLeading,
        String languages,
        String certification,
        String certificationNumber,
        String bmcInstitute,
        String bmcCertificateNumber,
        String amcInstitute,
        String amcCertificateNumber,
        String quote,
        long treksLed,
        Double rating,
        long reviewCount,
        List<TrackBrief> treks,
        long upcoming) {
}
