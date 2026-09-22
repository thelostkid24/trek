package com.sahyatri.catalog.service;

import com.sahyatri.catalog.dto.TrackRequest;
import com.sahyatri.catalog.dto.TrackResponse;
import com.sahyatri.catalog.entity.Track;
import com.sahyatri.catalog.repository.DepartureRepository;
import com.sahyatri.catalog.repository.TrackRepository;
import com.sahyatri.common.audit.AuditLog;
import com.sahyatri.common.exception.ApiException;
import com.sahyatri.common.storage.TrackPhotoFiles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TrackAdminService {

    private final TrackRepository tracks;
    private final DepartureRepository departures;
    private final TrackPhotoFiles photoFiles;
    private final AuditLog audit;

    public TrackAdminService(TrackRepository tracks, DepartureRepository departures, TrackPhotoFiles photoFiles,
                             AuditLog audit) {
        this.tracks = tracks;
        this.departures = departures;
        this.photoFiles = photoFiles;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<TrackResponse> list() {
        return tracks.findAllByOrderByNameAsc().stream().map(t -> TrackResponse.of(t, photoFiles)).toList();
    }

    @Transactional
    public TrackResponse create(TrackRequest req) {
        return save(Track.create(), req);
    }

    @Transactional
    public TrackResponse update(UUID id, TrackRequest req) {
        Track track = require(id);
        // Departures store an end date computed from the duration, so it's fixed once one exists.
        if (track.getDurationDays() != req.durationDays() && departures.existsByTrackId(id)) {
            throw ApiException.conflict("TRACK_IN_USE", "Duration can't change once the track has departures");
        }
        return save(track, req);
    }

    /** Shows or hides a track in the public catalog; tracks with upcoming dates show either way. */
    @Transactional
    public TrackResponse setListed(UUID adminId, UUID id, boolean listed) {
        Track track = require(id);
        if (track.isListed() != listed) {
            track.setListed(listed);
            audit.record(adminId, listed ? "TRACK_LISTED" : "TRACK_UNLISTED", "TRACK", id, Map.of());
        }
        return TrackResponse.of(tracks.saveAndFlush(track), photoFiles);
    }

    public Track require(UUID id) {
        return tracks.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TRACK_NOT_FOUND", "Track not found"));
    }

    private TrackResponse save(Track track, TrackRequest req) {
        if (tracks.existsBySlugAndIdNot(req.slug(), track.getId())) {
            throw ApiException.conflict("SLUG_TAKEN", "Another track already uses this slug");
        }
        List<String> itinerary = req.itinerary() == null ? List.of() : req.itinerary();
        if (!itinerary.isEmpty() && itinerary.size() != req.durationDays()) {
            throw ApiException.validation("itinerary", "must have one line for each of the " + req.durationDays() + " days");
        }
        checkBelowSummit("base_altitude_m", req.baseAltitudeM(), req.maxAltitudeM());
        checkBelowSummit("highest_camp_m", req.highestCampM(), req.maxAltitudeM());

        track.update(req.slug(), req.name().trim(), req.region().trim(), req.difficulty(), req.durationDays(),
                req.maxAltitudeM(), req.summary().trim(), req.description().trim(), req.meetingPoint().trim());
        track.updateRouteFacts(req.distanceKm(), req.baseAltitudeM(), req.highestCampM(), blankToNull(req.stay()),
                blankToNull(req.seasonLabel()));
        track.clearItinerary();
        tracks.saveAndFlush(track);
        itinerary.forEach(line -> track.addItineraryDay(line.trim()));
        return TrackResponse.of(tracks.saveAndFlush(track), photoFiles);
    }

    private static void checkBelowSummit(String field, Integer altitudeM, Integer maxAltitudeM) {
        if (altitudeM != null && maxAltitudeM != null && altitudeM > maxAltitudeM) {
            throw ApiException.validation(field, "can't be higher than the summit");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
