package com.sahyatri.guide.service;

import com.sahyatri.catalog.entity.Track;
import com.sahyatri.catalog.repository.TrackRepository;
import com.sahyatri.common.exception.ApiException;
import com.sahyatri.guide.dto.OwnRouteRequest;
import com.sahyatri.guide.dto.PriorTrek;
import com.sahyatri.guide.dto.RouteItem;
import com.sahyatri.guide.entity.GuideRoute;
import com.sahyatri.guide.entity.RouteAvailability;
import com.sahyatri.guide.repository.GuideRouteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The routes a guide leads (§7.21.8). Admins keep the record ({@link GuideRecordService}); the guide sets when they can
 * lead each one and a note. The trek, departure and guide pages read times led from here.
 */
@Service
public class GuideRouteService {

    private final GuideRouteRepository routes;
    private final TrackRepository tracks;

    public GuideRouteService(GuideRouteRepository routes, TrackRepository tracks) {
        this.routes = routes;
        this.tracks = tracks;
    }

    @Transactional(readOnly = true)
    public List<GuideRoute> forGuide(UUID guideId) {
        return routes.findByGuideIdOrderByPositionAsc(guideId);
    }

    /** Catalog treks with a times-led count, per guide: what the pages add completed departures to. */
    @Transactional(readOnly = true)
    public Map<UUID, List<PriorTrek>> priorTreks(Collection<UUID> guideIds) {
        return routes.findByGuideIdIn(guideIds).stream()
                .filter(r -> r.getTrackId() != null && r.getTimesLed() != null)
                .collect(Collectors.groupingBy(GuideRoute::getGuideId, Collectors.mapping(
                        r -> new PriorTrek(r.getTrackId(), r.getTimesLed()), Collectors.toList())));
    }

    /**
     * The guide's routes as they edit them, then each trek in {@code ledTrackIds} without a row yet (id null), so they
     * can add a note or dates to it.
     */
    @Transactional(readOnly = true)
    public List<RouteItem> items(UUID guideId, Collection<UUID> ledTrackIds) {
        List<GuideRoute> rows = forGuide(guideId);
        Set<UUID> withRow = rows.stream().map(GuideRoute::getTrackId).collect(Collectors.toSet());
        Set<UUID> trackIds = new HashSet<>(ledTrackIds);
        trackIds.addAll(withRow);
        trackIds.remove(null);
        Map<UUID, String> names = tracks.findAllById(trackIds).stream()
                .collect(Collectors.toMap(Track::getId, Track::getName));
        List<RouteItem> items = new ArrayList<>(rows.stream().map(r -> item(r, names)).toList());
        ledTrackIds.stream().filter(t -> !withRow.contains(t) && names.containsKey(t))
                .sorted(Comparator.comparing(names::get))
                .forEach(t -> items.add(new RouteItem(null, t, names.get(t), null, null, null, null, null, false,
                        RouteAvailability.ASK, null, null, null, null)));
        return items;
    }

    /**
     * The guide's own edits: availability and note on their rows, or a new row for a trek in {@code ledTrackIds}.
     * Rows they leave out stay as they are; only admins remove routes.
     */
    @Transactional
    public void updateOwn(UUID guideId, List<OwnRouteRequest> list, Set<UUID> ledTrackIds) {
        Map<UUID, GuideRoute> mine = forGuide(guideId).stream()
                .collect(Collectors.toMap(GuideRoute::getId, Function.identity()));
        Set<UUID> tracksWithRow = mine.values().stream().map(GuideRoute::getTrackId).collect(Collectors.toSet());
        Set<UUID> seen = new HashSet<>();
        int next = mine.values().stream().mapToInt(GuideRoute::getPosition).max().orElse(-1) + 1;
        Map<GuideRoute, OwnRouteRequest> changes = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            OwnRouteRequest r = list.get(i);
            String field = "routes[" + i + "]";
            GuideRoute row;
            if (r.id() != null) {
                row = mine.get(r.id());
                if (row == null) {
                    throw ApiException.validation(field + ".id", "isn't one of your routes");
                }
            } else {
                if (r.trackId() == null || !ledTrackIds.contains(r.trackId()) || tracksWithRow.contains(r.trackId())) {
                    throw ApiException.validation(field + ".track_id", "isn't a trek you lead");
                }
                row = new GuideRoute(guideId, r.trackId(), null, next++);
                tracksWithRow.add(r.trackId());
            }
            if (!seen.add(row.getId())) {
                throw ApiException.validation(field + ".id", "lists a route twice");
            }
            checkPlan(field, r.availability(), r.seasonFromMonth(), r.seasonToMonth(), r.availableFrom());
            changes.put(row, r);
        }
        changes.forEach((row, r) -> row.setPlan(orAsk(r.availability()), r.seasonFromMonth(), r.seasonToMonth(),
                r.availableFrom(), blankToNull(r.note())));
        routes.saveAllAndFlush(changes.keySet());
    }

    static RouteItem item(GuideRoute r, Map<UUID, String> trackNames) {
        return new RouteItem(r.getId(), r.getTrackId(), r.getTrackId() == null ? null : trackNames.get(r.getTrackId()),
                r.getName(), r.getKind(), r.getTimesLed(), r.getLastLedYear(), r.getLastLedMonth(), r.isMostRecent(),
                r.getAvailability(), r.getSeasonFromMonth(), r.getSeasonToMonth(), r.getAvailableFrom(), r.getNote());
    }

    /** A season goes with "any time" and needs both months; a date goes with "from a date" and nothing else. */
    static void checkPlan(String field, RouteAvailability availability, Integer seasonFrom, Integer seasonTo,
                          LocalDate availableFrom) {
        RouteAvailability a = orAsk(availability);
        if ((seasonFrom == null) != (seasonTo == null)) {
            throw ApiException.validation(field + ".season_to_month", "needs both months or neither");
        }
        if (seasonFrom != null && a != RouteAvailability.ANY_TIME) {
            throw ApiException.validation(field + ".season_from_month", "only goes with “any time”");
        }
        if ((availableFrom != null) != (a == RouteAvailability.FROM_DATE)) {
            throw ApiException.validation(field + ".available_from", a == RouteAvailability.FROM_DATE
                    ? "is required" : "only goes with “from a date”");
        }
    }

    static RouteAvailability orAsk(RouteAvailability a) {
        return a == null ? RouteAvailability.ASK : a;
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
