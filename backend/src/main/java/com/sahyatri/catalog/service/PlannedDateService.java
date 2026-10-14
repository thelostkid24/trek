package com.sahyatri.catalog.service;

import com.sahyatri.catalog.dto.PlannedDate;
import com.sahyatri.catalog.dto.PlannedDateRequest;
import com.sahyatri.catalog.entity.Track;
import com.sahyatri.catalog.entity.TrackPlannedDate;
import com.sahyatri.catalog.repository.TrackPlannedDateRepository;
import com.sahyatri.common.config.CatalogProperties;
import com.sahyatri.common.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Planned dates (§7.20): when a trek will run before a guide is confirmed for the date. Shown on the trek page as
 * "Guide: to be updated" and never bookable; booking is only ever through a published departure (law 3).
 */
@Service
public class PlannedDateService {

    private final TrackPlannedDateRepository dates;
    private final TrackAdminService tracks;
    private final CatalogProperties props;

    public PlannedDateService(TrackPlannedDateRepository dates, TrackAdminService tracks, CatalogProperties props) {
        this.dates = dates;
        this.tracks = tracks;
        this.props = props;
    }

    /** Every planned date of a track, past ones too, for the admin editor. */
    @Transactional(readOnly = true)
    public List<PlannedDate> all(UUID trackId) {
        Track track = tracks.require(trackId);
        return dates.findByTrackIdOrderByStartDateAsc(trackId).stream().map(d -> shown(track, d)).toList();
    }

    /** What the trek page shows: dates from today on, minus any a listed departure already starts on. */
    @Transactional(readOnly = true)
    public List<PlannedDate> upcoming(Track track, LocalDate today, Set<LocalDate> departureStarts) {
        return dates.findByTrackIdAndStartDateGreaterThanEqualOrderByStartDateAsc(track.getId(), today).stream()
                .filter(d -> !departureStarts.contains(d.getStartDate()))
                .map(d -> shown(track, d))
                .toList();
    }

    /** Replaces a track's planned dates. A date may not be in the past or repeat. */
    @Transactional
    public List<PlannedDate> replace(UUID trackId, List<PlannedDateRequest> list) {
        tracks.require(trackId);
        LocalDate today = LocalDate.now(props.zone());
        Set<LocalDate> seen = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            LocalDate start = list.get(i).startDate();
            if (start.isBefore(today)) {
                throw ApiException.validation("items[" + i + "].start_date", "must not be in the past");
            }
            if (!seen.add(start)) {
                throw ApiException.validation("items[" + i + "].start_date", "is already in the list");
            }
        }
        dates.deleteForTrack(trackId);
        list.stream()
                .sorted(Comparator.comparing(PlannedDateRequest::startDate))
                .forEach(d -> dates.save(new TrackPlannedDate(trackId, d.startDate(), TrackAdminService.blankToNull(d.note()))));
        dates.flush();
        return all(trackId);
    }

    private static PlannedDate shown(Track track, TrackPlannedDate d) {
        return new PlannedDate(d.getStartDate(), d.getStartDate().plusDays(track.getDurationDays() - 1L), d.getNote());
    }
}
