package com.sahyatri.guide.dto;

import java.util.UUID;

/**
 * A guide's name, photo, cover (3:1, null when none), home city and bio, as the guide pages show them, and the profile
 * URL's slug (§7.21).
 */
public record GuideProfileResponse(UUID guideId, String slug, String fullName, String avatarUrl, String coverUrl,
                                   String homeCity, String bio) {
}
