package com.sahyatri.guide.service;

import com.sahyatri.auth.entity.Role;
import com.sahyatri.auth.entity.User;
import com.sahyatri.auth.repository.UserRepository;
import com.sahyatri.common.config.CatalogProperties;
import com.sahyatri.common.exception.ApiException;
import com.sahyatri.guide.dto.AvailabilityRange;
import com.sahyatri.guide.dto.AvailabilityRequest;
import com.sahyatri.guide.dto.GuideAvailabilityItem;
import com.sahyatri.guide.dto.GuideDetailsResponse;
import com.sahyatri.guide.entity.GuideAvailability;
import com.sahyatri.guide.repository.GuideAvailabilityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The days a guide marks as free to lead (docs/TRD.md §7.21.7). Private to the guide and admins; dates and prices are
 * still set by our team. A guide's ranges never overlap: a range that shares a day with another is rejected (the guide
 * edits the existing one instead), checked under a lock on the guide's user row so two saves can't both slip in. Past
 * ranges are kept but no longer listed to the guide.
 */
@Service
public class GuideAvailabilityService {

    /** Widest window an admin can ask for at once. */
    static final int MAX_WINDOW_DAYS = 400;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final GuideAvailabilityRepository ranges;
    private final UserRepository users;
    private final GuideDetailsService guideDetails;
    private final CatalogProperties catalog;
    private final JdbcTemplate jdbc;

    public GuideAvailabilityService(GuideAvailabilityRepository ranges, UserRepository users,
                                    GuideDetailsService guideDetails, CatalogProperties catalog, JdbcTemplate jdbc) {
        this.ranges = ranges;
        this.users = users;
        this.guideDetails = guideDetails;
        this.catalog = catalog;
        this.jdbc = jdbc;
    }

    /** Ranges that haven't ended yet, soonest first. */
    @Transactional(readOnly = true)
    public List<AvailabilityRange> list(UUID guideId) {
        return ranges.findByGuideIdAndEndDateGreaterThanEqualOrderByStartDateAsc(guideId, today()).stream()
                .map(GuideAvailabilityService::toRange).toList();
    }

    @Transactional
    public AvailabilityRange create(UUID guideId, AvailabilityRequest req) {
        check(req);
        lockGuide(guideId);
        rejectOverlap(guideId, null, req);
        GuideAvailability range = new GuideAvailability(guideId, req.startDate(), req.endDate(), blankToNull(req.note()));
        return toRange(ranges.save(range));
    }

    @Transactional
    public AvailabilityRange update(UUID guideId, UUID id, AvailabilityRequest req) {
        check(req);
        lockGuide(guideId);
        GuideAvailability range = require(guideId, id);
        rejectOverlap(guideId, id, req);
        range.change(req.startDate(), req.endDate(), blankToNull(req.note()));
        return toRange(range);
    }

    @Transactional
    public void delete(UUID guideId, UUID id) {
        ranges.delete(require(guideId, id));
    }

    /** Every active guide's ranges that share a day with [from, to], for admins planning dates. */
    @Transactional(readOnly = true)
    public List<GuideAvailabilityItem> inWindow(LocalDate from, LocalDate to) {
        if (from == null) {
            throw ApiException.validation("from", "is required");
        }
        if (to == null) {
            throw ApiException.validation("to", "is required");
        }
        if (to.isBefore(from)) {
            throw ApiException.validation("to", "must not be before from");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_WINDOW_DAYS) {
            throw ApiException.validation("to", "must be within " + MAX_WINDOW_DAYS + " days of from");
        }
        List<GuideAvailability> found = ranges.findInWindow(from, to);
        if (found.isEmpty()) {
            return List.of();
        }
        Set<UUID> ids = found.stream().map(GuideAvailability::getGuideId).collect(Collectors.toSet());
        Map<UUID, User> guides = users.findAllById(ids).stream()
                .filter(u -> u.getRole() == Role.GUIDE && !u.isDisabled())
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, GuideDetailsResponse> details = guideDetails.forGuides(guides.keySet());
        return found.stream()
                .filter(a -> guides.containsKey(a.getGuideId()))
                .map(a -> new GuideAvailabilityItem(a.getId(), a.getGuideId(), guides.get(a.getGuideId()).getFullName(),
                        details.get(a.getGuideId()).slug(), a.getStartDate(), a.getEndDate(), a.getNote()))
                .toList();
    }

    private void check(AvailabilityRequest req) {
        if (req.endDate().isBefore(req.startDate())) {
            throw ApiException.validation("end_date", "must not be before the start date");
        }
        if (req.endDate().isBefore(today())) {
            throw ApiException.validation("end_date", "must not be in the past");
        }
    }

    private void rejectOverlap(UUID guideId, UUID self, AvailabilityRequest req) {
        ranges.findOverlapping(guideId, req.startDate(), req.endDate()).stream()
                .filter(a -> !a.getId().equals(self))
                .findFirst()
                .ifPresent(a -> {
                    throw new ApiException(HttpStatus.CONFLICT, "AVAILABILITY_OVERLAP",
                            "You're already free " + DAY.format(a.getStartDate()) + " – " + DAY.format(a.getEndDate())
                                    + ". Change that range instead.",
                            Map.of("id", a.getId(), "start_date", a.getStartDate(), "end_date", a.getEndDate()));
                });
    }

    /** Serialises a guide's saves, so the overlap check sees any range committed just before. */
    private void lockGuide(UUID guideId) {
        jdbc.queryForObject("SELECT id FROM users WHERE id = ? FOR UPDATE", UUID.class, guideId);
    }

    private GuideAvailability require(UUID guideId, UUID id) {
        return ranges.findByIdAndGuideId(id, guideId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "AVAILABILITY_NOT_FOUND", "That range isn't in your availability"));
    }

    private LocalDate today() {
        return LocalDate.now(catalog.zone());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static AvailabilityRange toRange(GuideAvailability a) {
        return new AvailabilityRange(a.getId(), a.getStartDate(), a.getEndDate(), a.getNote());
    }
}
