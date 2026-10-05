package com.sahyatri.guide.dto;

import java.util.UUID;

/** A guide's name, photo, home city and bio, as the guide pages show them. */
public record GuideProfileResponse(UUID guideId, String fullName, String avatarUrl, String homeCity, String bio) {
}
