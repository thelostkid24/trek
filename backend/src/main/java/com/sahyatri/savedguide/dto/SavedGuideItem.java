package com.sahyatri.savedguide.dto;

import java.time.Instant;
import java.util.UUID;

/** One saved guide as the trekker's list shows it. The rating is computed at read time (law 8). */
public record SavedGuideItem(
        UUID guideId,
        String slug,
        String fullName,
        String avatarUrl,
        String homeCity,
        Double rating,
        long reviewCount,
        Instant savedAt) {
}
