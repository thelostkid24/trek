package com.sahyatri.catalog.dto;

import java.util.List;
import java.util.UUID;

/**
 * One guide on the public guides list (§7.17): who they are, their credentials, record and rating, the catalog treks
 * they've led (most often first) and how many upcoming dates they lead. {@code treksLed} adds every route's times led
 * before Sahyātri, listed or not, to departures completed with us, as the guide page does. {@code routes}: the names
 * of their routes in the kept order, then catalog treks completed with us that aren't routes (§7.21.8).
 * {@code certified}: a verified certificate still valid today (§7.21.3). Computed at read time (law 8).
 */
public record GuideListItem(
        UUID id,
        String slug,
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
        long upcoming,
        List<String> routes,
        boolean certified) {
}
