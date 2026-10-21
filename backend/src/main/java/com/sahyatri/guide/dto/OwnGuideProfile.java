package com.sahyatri.guide.dto;

import java.util.List;
import java.util.UUID;

/**
 * The guide's own profile as they edit it (§7.21). {@code specialtyOptions} is the list to pick from and
 * {@code routes} the treks they may write a note about; both are read-only.
 */
public record OwnGuideProfile(
        UUID guideId,
        String slug,
        String fullName,
        String avatarUrl,
        String coverUrl,
        String homeCity,
        String bio,
        String quote,
        String languages,
        Integer leadingSince,
        List<UUID> specialtyIds,
        List<Milestone> milestones,
        List<RouteNote> routeNotes,
        List<Specialty> specialtyOptions,
        List<RouteOption> routes) {
}
