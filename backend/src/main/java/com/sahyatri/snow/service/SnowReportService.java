package com.sahyatri.snow.service;

import com.sahyatri.auth.entity.Role;
import com.sahyatri.auth.entity.User;
import com.sahyatri.auth.repository.UserRepository;
import com.sahyatri.auth.service.CurrentUser;
import com.sahyatri.catalog.entity.DepartureStatus;
import com.sahyatri.catalog.repository.DepartureRepository;
import com.sahyatri.catalog.service.TrackAdminService;
import com.sahyatri.catalog.service.TrackPhotoService;
import com.sahyatri.common.config.CatalogProperties;
import com.sahyatri.common.exception.ApiException;
import com.sahyatri.common.storage.FileStorage;
import com.sahyatri.common.storage.Images;
import com.sahyatri.common.storage.SnowPhotoFiles;
import com.sahyatri.snow.dto.Condition;
import com.sahyatri.snow.dto.CrowdCount;
import com.sahyatri.snow.dto.ReportableTrack;
import com.sahyatri.snow.dto.SnowReportRequest;
import com.sahyatri.snow.dto.SnowReportResponse;
import com.sahyatri.snow.entity.SnowReport;
import com.sahyatri.snow.repository.SnowReportRepository;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Weekly trail conditions (§7.13). Admins report on any trek; a guide reports on treks they have a published or
 * completed departure on. Reports are never edited or deleted: a correction is a newer report.
 */
@Service
public class SnowReportService {

    static final int HISTORY = 52;
    static final int CROWD_HISTORY = 12;
    private static final Set<DepartureStatus> LEADING = Set.of(DepartureStatus.PUBLISHED, DepartureStatus.COMPLETED);

    private final SnowReportRepository reports;
    private final TrackAdminService tracks;
    private final DepartureRepository departures;
    private final UserRepository users;
    private final CurrentUser currentUser;
    private final FileStorage storage;
    private final SnowPhotoFiles photoFiles;
    private final CatalogProperties catalog;
    private final ObjectMapper json;

    public SnowReportService(SnowReportRepository reports, TrackAdminService tracks, DepartureRepository departures,
                             UserRepository users, CurrentUser currentUser, FileStorage storage,
                             SnowPhotoFiles photoFiles, CatalogProperties catalog, ObjectMapper json) {
        this.reports = reports;
        this.tracks = tracks;
        this.departures = departures;
        this.users = users;
        this.currentUser = currentUser;
        this.storage = storage;
        this.photoFiles = photoFiles;
        this.catalog = catalog;
        this.json = json;
    }

    /** Treks the guide can report on. */
    @Transactional(readOnly = true)
    public List<ReportableTrack> reportableTracks(UUID guideId) {
        currentUser.require(guideId);
        return departures.findTracksLedBy(guideId, LEADING).stream()
                .map(t -> new ReportableTrack(t.getId(), t.getSlug(), t.getName()))
                .toList();
    }

    /** The last year of reports, newest first. */
    @Transactional(readOnly = true)
    public List<SnowReportResponse> history(UUID userId, UUID trackId) {
        requireReporter(userId, trackId);
        return toResponses(reports.findByTrackIdOrderByReportedOnDescCreatedAtDesc(trackId, Limit.of(HISTORY)));
    }

    @Transactional
    public SnowReportResponse create(UUID userId, UUID trackId, SnowReportRequest req) {
        requireReporter(userId, trackId);
        LocalDate today = LocalDate.now(catalog.zone());
        if (req.reportedOn().isAfter(today)) {
            throw ApiException.validation("reported_on", "can't be in the future");
        }
        String crowdPlace = blankToNull(req.crowdPlace());
        if ((crowdPlace == null) != (req.crowdTents() == null)) {
            throw ApiException.validation(crowdPlace == null ? "crowd_place" : "crowd_tents",
                    "give both the tent count and where it was counted");
        }
        List<Condition> conditions = req.conditions() == null ? List.of() : req.conditions().stream()
                .map(c -> new Condition(c.label().trim(), c.value().trim()))
                .toList();
        SnowReport report = new SnowReport(trackId, userId, req.reportedOn(), req.reportedFrom().trim(),
                req.snowlineM(), req.nightTempC(), json.writeValueAsString(conditions), crowdPlace, req.crowdTents(),
                blankToNull(req.note()));
        return toResponses(List.of(reports.saveAndFlush(report))).getFirst();
    }

    /** One photo per report, taken that morning. The file is written before the flag, so the URL never 404s. */
    @Transactional
    public SnowReportResponse addPhoto(UUID userId, UUID reportId, MultipartFile file) {
        SnowReport report = reports.findByIdForUpdate(reportId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "Report not found"));
        requireReporter(userId, report.getTrackId());
        if (report.hasPhoto()) {
            throw ApiException.conflict("REPORT_HAS_PHOTO", "This report already has its photo");
        }
        storage.put(SnowPhotoFiles.storageKey(reportId), TrackPhotoService.toPhotoJpeg(Images.read(file)));
        report.photoAdded();
        return toResponses(List.of(reports.saveAndFlush(report))).getFirst();
    }

    /** The newest report on a trek, for its public page. */
    @Transactional(readOnly = true)
    public Optional<SnowReportResponse> latest(UUID trackId) {
        return reports.findLatest(trackId).map(r -> toResponses(List.of(r)).getFirst());
    }

    /** Recent tent counts, oldest first, for the trek page's crowd history. */
    @Transactional(readOnly = true)
    public List<CrowdCount> crowd(UUID trackId) {
        return reports.findByTrackIdAndCrowdTentsNotNullOrderByReportedOnDescCreatedAtDesc(trackId,
                        Limit.of(CROWD_HISTORY)).stream()
                .map(r -> new CrowdCount(r.getReportedOn(), r.getCrowdPlace(), r.getCrowdTents()))
                .toList()
                .reversed();
    }

    private void requireReporter(UUID userId, UUID trackId) {
        User user = currentUser.require(userId);
        tracks.require(trackId);
        boolean allowed = user.getRole() == Role.ADMIN
                || (user.getRole() == Role.GUIDE
                && departures.existsByTrackIdAndGuideIdAndStatusIn(trackId, userId, LEADING));
        if (!allowed) {
            throw ApiException.forbidden("You can only report on treks you lead");
        }
    }

    private List<SnowReportResponse> toResponses(List<SnowReport> rows) {
        Map<UUID, String> names = new HashMap<>();
        users.findAllById(rows.stream().map(SnowReport::getReportedBy).distinct().toList())
                .forEach(u -> names.put(u.getId(), u.getFullName()));
        return rows.stream().map(r -> new SnowReportResponse(r.getId(), r.getReportedOn(), r.getReportedFrom(),
                r.getSnowlineM(), r.getNightTempC(),
                json.readValue(r.getConditions(), new TypeReference<List<Condition>>() {
                }),
                r.getCrowdPlace(), r.getCrowdTents(), r.getNote(), r.hasPhoto() ? photoFiles.url(r.getId()) : null,
                new SnowReportResponse.Reporter(r.getReportedBy(), names.get(r.getReportedBy())), r.getCreatedAt()))
                .toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
