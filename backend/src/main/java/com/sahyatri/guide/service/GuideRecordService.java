package com.sahyatri.guide.service;

import com.sahyatri.auth.entity.Role;
import com.sahyatri.auth.repository.UserRepository;
import com.sahyatri.catalog.repository.TrackRepository;
import com.sahyatri.common.audit.AuditLog;
import com.sahyatri.common.config.CatalogProperties;
import com.sahyatri.common.exception.ApiException;
import com.sahyatri.guide.dto.GuideRecord;
import com.sahyatri.guide.dto.GuideRecordRequest;
import com.sahyatri.guide.dto.RouteRequest;
import com.sahyatri.guide.dto.StatPick;
import com.sahyatri.guide.entity.CertificationType;
import com.sahyatri.guide.entity.GuideCertification;
import com.sahyatri.guide.entity.GuideDetails;
import com.sahyatri.guide.entity.GuidePledge;
import com.sahyatri.guide.entity.GuideProfileStat;
import com.sahyatri.guide.entity.GuideRoute;
import com.sahyatri.guide.entity.GuideRouteKind;
import com.sahyatri.guide.entity.GuideStatKey;
import com.sahyatri.guide.entity.PledgeKind;
import com.sahyatri.guide.repository.GuideCertificationRepository;
import com.sahyatri.guide.repository.GuideDetailsRepository;
import com.sahyatri.guide.repository.GuidePledgeRepository;
import com.sahyatri.guide.repository.GuideProfileStatRepository;
import com.sahyatri.guide.repository.GuideRouteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A guide's record (§7.21.8), kept by admins from onboarding: treks led elsewhere in the last three years, the routes
 * with how often and when they last led them, the safety pledges, and which numbers the stats row shows.
 */
@Service
public class GuideRecordService {

    private final GuideRouteRepository routes;
    private final GuidePledgeRepository pledges;
    private final GuideProfileStatRepository stats;
    private final GuideDetailsRepository details;
    private final GuideCertificationRepository certifications;
    private final UserRepository users;
    private final TrackRepository tracks;
    private final AuditLog audit;
    private final CatalogProperties catalog;

    public GuideRecordService(GuideRouteRepository routes, GuidePledgeRepository pledges,
                              GuideProfileStatRepository stats, GuideDetailsRepository details,
                              GuideCertificationRepository certifications, UserRepository users,
                              TrackRepository tracks, AuditLog audit, CatalogProperties catalog) {
        this.routes = routes;
        this.pledges = pledges;
        this.stats = stats;
        this.details = details;
        this.certifications = certifications;
        this.users = users;
        this.tracks = tracks;
        this.audit = audit;
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public GuideRecord get(UUID guideId) {
        requireGuide(guideId);
        return toResponse(guideId);
    }

    @Transactional
    public GuideRecord update(UUID adminId, UUID guideId, GuideRecordRequest req) {
        requireGuide(guideId);
        List<RouteRequest> list = req.routes() == null ? List.of() : req.routes();
        List<StatPick> picks = req.stats() == null ? List.of() : req.stats();
        Set<PledgeKind> newPledges = req.pledges() == null || req.pledges().isEmpty()
                ? EnumSet.noneOf(PledgeKind.class) : EnumSet.copyOf(req.pledges());

        // Check everything before writing anything.
        Map<UUID, GuideRoute> existing = routes.findByGuideIdOrderByPositionAsc(guideId).stream()
                .collect(Collectors.toMap(GuideRoute::getId, Function.identity()));
        Set<UUID> ids = new HashSet<>();
        Set<UUID> trackIds = new HashSet<>();
        int thisYear = today().getYear();
        for (int i = 0; i < list.size(); i++) {
            RouteRequest r = list.get(i);
            String field = "routes[" + i + "]";
            if (r.id() != null && (!existing.containsKey(r.id()) || !ids.add(r.id()))) {
                throw ApiException.validation(field + ".id", "isn't one of this guide's routes");
            }
            String name = GuideRouteService.blankToNull(r.name());
            if ((r.trackId() == null) == (name == null)) {
                throw ApiException.validation(field + ".name", "pick a trek or type a name, not both");
            }
            if (r.trackId() != null && !trackIds.add(r.trackId())) {
                throw ApiException.validation(field + ".track_id", "lists a trek twice");
            }
            if (r.lastLedMonth() != null && r.lastLedYear() == null) {
                throw ApiException.validation(field + ".last_led_year", "is required with a month");
            }
            if (r.lastLedYear() != null && r.lastLedYear() > thisYear) {
                throw ApiException.validation(field + ".last_led_year", "can't be in the future");
            }
            GuideRouteService.checkPlan(field, r.availability(), r.seasonFromMonth(), r.seasonToMonth(),
                    r.availableFrom());
        }
        if (tracks.findAllById(trackIds).size() != trackIds.size()) {
            throw ApiException.validation("routes", "names a trek that doesn't exist");
        }
        for (int i = 0; i < picks.size(); i++) {
            StatPick p = picks.get(i);
            boolean needsRoute = p.key() == GuideStatKey.ROUTE_TIMES;
            if (needsRoute != (p.routeIndex() != null)) {
                throw ApiException.validation("stats[" + i + "].route_index",
                        needsRoute ? "is required" : "only goes with a route's times led");
            }
            if (needsRoute && p.routeIndex() >= list.size()) {
                throw ApiException.validation("stats[" + i + "].route_index", "isn't one of the routes");
            }
        }

        // Routes: update kept rows, add new ones, then remove the rest (their stat picks go with them).
        stats.deleteForGuide(guideId);
        List<GuideRoute> saved = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            RouteRequest r = list.get(i);
            String name = GuideRouteService.blankToNull(r.name());
            GuideRoute row = r.id() != null ? existing.remove(r.id()) : new GuideRoute(guideId, r.trackId(), name, i);
            row.setRecord(r.trackId(), name, r.kind() == null ? GuideRouteKind.TREK : r.kind(), r.timesLed(),
                    r.lastLedYear(), r.lastLedMonth(), Boolean.TRUE.equals(r.mostRecent()), i);
            row.setPlan(GuideRouteService.orAsk(r.availability()), r.seasonFromMonth(), r.seasonToMonth(),
                    r.availableFrom(), GuideRouteService.blankToNull(r.note()));
            saved.add(row);
        }
        routes.deleteAll(existing.values());
        routes.flush();
        List<GuideRoute> byIndex = routes.saveAllAndFlush(saved);
        stats.saveAllAndFlush(IntStream.range(0, picks.size()).mapToObj(i -> new GuideProfileStat(guideId, i,
                picks.get(i).key(), picks.get(i).routeIndex() == null ? null
                : byIndex.get(picks.get(i).routeIndex()).getId())).toList());

        Set<PledgeKind> oldPledges = pledges.findByGuideId(guideId).stream().map(GuidePledge::getKind)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(PledgeKind.class)));
        if (!oldPledges.equals(newPledges)) {
            pledges.deleteForGuide(guideId);
            pledges.saveAllAndFlush(newPledges.stream().map(k -> new GuidePledge(guideId, k, adminId)).toList());
            audit.record(adminId, "GUIDE_PLEDGES_RECORDED", "USER", guideId,
                    Map.of("before", oldPledges.stream().map(Enum::name).sorted().toList(),
                            "after", newPledges.stream().map(Enum::name).sorted().toList()));
        }

        GuideDetails d = details.findById(guideId).orElseGet(() -> new GuideDetails(guideId));
        d.setTreksLast3y(req.treksLast3y());
        details.saveAndFlush(d);
        return toResponse(guideId);
    }

    /** What the public page needs from the record: read-only, nothing created. */
    @Transactional(readOnly = true)
    public PageRecord pageRecord(UUID guideId) {
        return new PageRecord(details.findById(guideId).map(GuideDetails::getTreksLast3y).orElse(null),
                routes.findByGuideIdOrderByPositionAsc(guideId), pledges.kindsFor(guideId),
                stats.findByGuideIdOrderByPositionAsc(guideId),
                certifications.findByGuideIdOrderByCreatedAtAsc(guideId).stream()
                        .filter(c -> c.getType() == CertificationType.BMC || c.getType() == CertificationType.AMC)
                        .sorted(Comparator.comparing(GuideCertification::getType))
                        .toList());
    }

    /** {@code courses}: BMC and AMC rows, whatever their status, basic first. */
    public record PageRecord(Integer treksLast3y, List<GuideRoute> routes, List<PledgeKind> pledges,
                             List<GuideProfileStat> stats, List<GuideCertification> courses) {
    }

    private GuideRecord toResponse(UUID guideId) {
        List<GuideRoute> rows = routes.findByGuideIdOrderByPositionAsc(guideId);
        Map<UUID, String> names = new HashMap<>();
        tracks.findAllById(rows.stream().map(GuideRoute::getTrackId).filter(Objects::nonNull).toList())
                .forEach(t -> names.put(t.getId(), t.getName()));
        Map<UUID, Integer> indexOf = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            indexOf.put(rows.get(i).getId(), i);
        }
        List<StatPick> picks = stats.findByGuideIdOrderByPositionAsc(guideId).stream()
                .map(s -> new StatPick(s.getStatKey(), s.getRouteId() == null ? null : indexOf.get(s.getRouteId())))
                .toList();
        return new GuideRecord(details.findById(guideId).map(GuideDetails::getTreksLast3y).orElse(null),
                rows.stream().map(r -> GuideRouteService.item(r, names)).toList(), pledges.kindsFor(guideId), picks);
    }

    private void requireGuide(UUID guideId) {
        users.findById(guideId)
                .filter(u -> u.getRole() == Role.GUIDE)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GUIDE_NOT_FOUND", "Guide not found"));
    }

    private LocalDate today() {
        return LocalDate.now(catalog.zone());
    }
}
