package com.sahyatri.catalog.dto;

import com.sahyatri.catalog.entity.Difficulty;
import com.sahyatri.catalog.entity.Season;

import java.util.List;

/**
 * One trek in the public catalog: the card facts, its cover photo (first uploaded, or null) and every upcoming
 * published departure, soonest first. Empty {@code departures} means "dates coming soon". {@code homeOrder} is its place
 * in the home page's "This season" row (null: not shown there).
 */
public record CatalogTrek(
        String slug,
        String name,
        String region,
        Difficulty difficulty,
        int durationDays,
        String summary,
        Integer maxAltitudeM,
        String seasonLabel,
        List<Season> seasons,
        Integer homeOrder,
        String coverUrl,
        List<CatalogDeparture> departures) {
}
