package com.sahyatri.admin.dto;

import java.time.Instant;
import java.util.UUID;

public record GuideResponse(UUID id, String fullName, String email, String phone, String avatarUrl,
                            Instant createdAt) {
}
