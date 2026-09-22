package com.sahyatri.catalog.service;

import com.sahyatri.auth.entity.Role;
import com.sahyatri.auth.entity.User;
import com.sahyatri.auth.repository.UserRepository;
import com.sahyatri.auth.service.CurrentUser;
import com.sahyatri.catalog.dto.AdminDepartureResponse;
import com.sahyatri.catalog.dto.CancelDepartureRequest;
import com.sahyatri.catalog.dto.DepartureRequest;
import com.sahyatri.catalog.entity.Departure;
import com.sahyatri.catalog.entity.DepartureStatus;
import com.sahyatri.catalog.repository.DepartureRepository;
import com.sahyatri.common.audit.AuditLog;
import com.sahyatri.common.config.CatalogProperties;
import com.sahyatri.common.exception.ApiException;
import com.sahyatri.common.storage.AvatarFiles;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Admin lifecycle of a departure: draft → published → cancelled (force majeure only). */
@Service
public class DepartureAdminService {

    static final String ENTITY = "DEPARTURE";

    private final DepartureRepository departures;
    private final TrackAdminService tracks;
    private final UserRepository users;
    private final CatalogService catalog;
    private final CatalogProperties props;
    private final AuditLog audit;
    private final AvatarFiles avatars;
    private final CurrentUser currentUser;
    private final ApplicationEventPublisher events;

    public DepartureAdminService(DepartureRepository departures, TrackAdminService tracks, UserRepository users,
                                 CatalogService catalog, CatalogProperties props, AuditLog audit,
                                 AvatarFiles avatars, CurrentUser currentUser, ApplicationEventPublisher events) {
        this.events = events;
        this.currentUser = currentUser;
        this.departures = departures;
        this.tracks = tracks;
        this.users = users;
        this.catalog = catalog;
        this.props = props;
        this.audit = audit;
        this.avatars = avatars;
    }

    @Transactional(readOnly = true)
    public List<AdminDepartureResponse> list() {
        return departures.findAllForAdmin().stream().map(this::toResponse).toList();
    }

    @Transactional
    public AdminDepartureResponse create(UUID adminId, DepartureRequest req) {
        currentUser.require(adminId);
        Departure departure = Departure.draft();
        plan(departure, req);
        departures.saveAndFlush(departure);
        audit.record(adminId, "DEPARTURE_CREATED", ENTITY, departure.getId(), Map.of());
        return toResponse(departure);
    }

    @Transactional
    public AdminDepartureResponse update(UUID id, DepartureRequest req) {
        Departure departure = lock(id);
        requireDraft(departure);
        plan(departure, req);
        return toResponse(departures.saveAndFlush(departure));
    }

    @Transactional
    public void delete(UUID id) {
        Departure departure = lock(id);
        requireDraft(departure);
        departures.delete(departure);
    }

    @Transactional
    public AdminDepartureResponse publish(UUID adminId, UUID id) {
        currentUser.require(adminId);
        Departure departure = lock(id);
        assertPublishable(departure);
        departure.publish(props.guideShareBps());
        departures.saveAndFlush(departure);
        audit.record(adminId, "DEPARTURE_PUBLISHED", ENTITY, id,
                Map.of("guide_share_bps", props.guideShareBps()));
        return toResponse(departure);
    }

    /** Force majeure (law 4). The booking feature cancels and fully refunds every booking in this transaction. */
    @Transactional
    public AdminDepartureResponse cancel(UUID adminId, UUID id, CancelDepartureRequest req) {
        currentUser.require(adminId);
        Departure departure = lock(id);
        if (departure.getStatus() != DepartureStatus.PUBLISHED
                || !catalog.today().isBefore(departure.getStartDate())) {
            throw ApiException.conflict("DEPARTURE_NOT_CANCELLABLE",
                    "Only a published departure that hasn't started can be cancelled");
        }
        departure.cancel(req.reasonCode(), req.reasonNote().trim());
        events.publishEvent(new DepartureCancelledEvent(id, adminId));
        departures.saveAndFlush(departure);
        audit.record(adminId, "DEPARTURE_CANCELLED", ENTITY, id,
                Map.of("reason_code", req.reasonCode().name(), "reason_note", departure.getCancelReasonNote()));
        return toResponse(departure);
    }

    /** Publish gate. Law 3's protocol acknowledgement check belongs here once guides own departures. */
    private void assertPublishable(Departure departure) {
        requireDraft(departure);
        requireGuide(departure.getGuide().getId());
        if (departure.getStartDate().isBefore(catalog.today().plusDays(props.bookingCutoffDays()))) {
            throw ApiException.conflict("START_DATE_TOO_SOON",
                    "The start date must be at least " + props.bookingCutoffDays() + " day(s) away to publish");
        }
    }

    private void plan(Departure departure, DepartureRequest req) {
        if (req.startDate().isBefore(catalog.today())) {
            throw ApiException.validation("start_date", "must be today or later");
        }
        departure.plan(tracks.require(req.trackId()), requireGuide(req.guideId()), req.startDate(),
                req.pricePaise(), req.maxGroupSize());
    }

    private User requireGuide(UUID guideId) {
        return users.findById(guideId)
                .filter(u -> u.getRole() == Role.GUIDE && !u.isDisabled())
                .orElseThrow(() -> ApiException.conflict("NOT_A_GUIDE", "The selected person isn't an active guide"));
    }

    private Departure lock(UUID id) {
        return departures.findByIdForUpdate(id).orElseThrow(CatalogService::departureNotFound);
    }

    private static void requireDraft(Departure departure) {
        if (!departure.isDraft()) {
            throw ApiException.conflict("DEPARTURE_NOT_DRAFT", "Only a draft departure can be changed");
        }
    }

    private AdminDepartureResponse toResponse(Departure d) {
        var t = d.getTrack();
        var g = d.getGuide();
        return new AdminDepartureResponse(d.getId(),
                new AdminDepartureResponse.TrackRef(t.getId(), t.getSlug(), t.getName(), t.getDurationDays()),
                new AdminDepartureResponse.GuideRef(g.getId(), g.getFullName(), g.getEmail(),
                        avatars.url(g.getAvatarKey())),
                d.getStartDate(), d.getEndDate(), d.getPricePaise(), d.getMaxGroupSize(), d.getSeatsTaken(),
                d.getStatus(), d.getGuideShareBps(), d.getPublishedAt(), d.getCancelledAt(),
                d.getCancelReasonCode(), d.getCancelReasonNote(), d.getCreatedAt(), d.getUpdatedAt());
    }
}
