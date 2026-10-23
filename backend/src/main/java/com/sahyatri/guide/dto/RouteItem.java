package com.sahyatri.guide.dto;

import com.sahyatri.guide.entity.GuideRouteKind;
import com.sahyatri.guide.entity.RouteAvailability;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A route as the admin's record form and the guide's own profile show it (§7.21.8). {@code trackName} is the catalog
 * trek's name when {@code trackId} is set. {@code id} is null for a trek the guide has led or leads with us but nobody
 * has added yet; saving it creates the row.
 */
public record RouteItem(UUID id, UUID trackId, String trackName, String name, GuideRouteKind kind, Integer timesLed,
                        Integer lastLedYear, Integer lastLedMonth, boolean mostRecent, RouteAvailability availability,
                        Integer seasonFromMonth, Integer seasonToMonth, LocalDate availableFrom, String note) {
}
