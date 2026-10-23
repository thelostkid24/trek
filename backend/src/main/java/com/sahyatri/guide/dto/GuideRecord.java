package com.sahyatri.guide.dto;

import com.sahyatri.guide.entity.PledgeKind;

import java.util.List;

/**
 * A guide's record as admins keep it (§7.21.8): treks led elsewhere in the last three years, their routes, the safety
 * pledges and which numbers the stats row shows (empty: the default row).
 */
public record GuideRecord(Integer treksLast3y, List<RouteItem> routes, List<PledgeKind> pledges, List<StatPick> stats) {
}
