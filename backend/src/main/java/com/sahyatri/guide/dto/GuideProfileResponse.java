package com.sahyatri.guide.dto;

import java.util.UUID;

/** A guide's name, photo, home city and bio, as the guide pages show them, and the profile URL's slug (§7.21). */
public record GuideProfileResponse(UUID guideId, String slug, String fullName, String avatarUrl, String homeCity,
                                   String bio) {
}
