package com.sahyatri.guide.dto;

import java.util.List;
import java.util.UUID;

/**
 * The guide's own profile as they edit it (§7.21). {@code specialtyOptions} is the list to pick from (read-only).
 * {@code routes}: their routes, then treks they've led or lead with us that have no row yet (id null); they set when
 * they can lead each and a note, the rest is read-only.
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
        List<Specialty> specialtyOptions,
        List<RouteItem> routes) {
}
