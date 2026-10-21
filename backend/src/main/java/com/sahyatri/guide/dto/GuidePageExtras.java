package com.sahyatri.guide.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** What the public guide page adds from the guide's own profile (§7.21): cover, specialties, milestones, route notes. */
public record GuidePageExtras(String coverUrl, List<String> specialties, List<Milestone> milestones,
                              Map<UUID, String> routeNotes) {
}
