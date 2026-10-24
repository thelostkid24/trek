package com.sahyatri.catalog.dto;

import com.sahyatri.catalog.entity.Difficulty;
import com.sahyatri.catalog.entity.Track;

/** Track fields shown on a public departure card. */
public record TrackBrief(String slug, String name, String region, String state, Difficulty difficulty, int durationDays) {

    public static TrackBrief of(Track t) {
        return new TrackBrief(t.getSlug(), t.getName(), t.getRegion(), t.getState(), t.getDifficulty(), t.getDurationDays());
    }
}
