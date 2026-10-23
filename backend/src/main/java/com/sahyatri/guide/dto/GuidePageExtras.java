package com.sahyatri.guide.dto;

import java.util.List;

/** What the public guide page adds from the guide's own profile (§7.21): cover, specialties, milestones. */
public record GuidePageExtras(String coverUrl, List<String> specialties, List<Milestone> milestones) {
}
