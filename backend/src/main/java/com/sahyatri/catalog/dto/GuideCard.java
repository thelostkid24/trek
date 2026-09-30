package com.sahyatri.catalog.dto;

import java.util.UUID;

/**
 * A departure's guide as the trek and departure pages show it: "Sankri · 6 years leading · led Kedarkantha 34 times",
 * their bio, credentials and rating. Counts and the rating are computed at read time (law 8); credentials may be null.
 */
public record GuideCard(
        UUID id,
        String fullName,
        String avatarUrl,
        String homeCity,
        String bio,
        long ledThisTrek,
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
        long reviewCount) {
}
