package com.sahyatri.snow.controller;

import com.sahyatri.common.web.ItemsResponse;
import com.sahyatri.snow.dto.ReportableTrack;
import com.sahyatri.snow.dto.SnowReportRequest;
import com.sahyatri.snow.dto.SnowReportResponse;
import com.sahyatri.snow.service.SnowReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Contract: docs/TRD.md §7.13. The same calls under /api/admin (any trek) and /api/guide (treks they lead); the
 * path prefix picks the role in SecurityConfig and the service checks the trek.
 */
@RestController
public class SnowReportController {

    private final SnowReportService reports;

    public SnowReportController(SnowReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/api/guide/tracks")
    public ItemsResponse<ReportableTrack> guideTracks(@AuthenticationPrincipal Jwt jwt) {
        return new ItemsResponse<>(reports.reportableTracks(userId(jwt)));
    }

    @GetMapping({"/api/admin/tracks/{id}/snow-reports", "/api/guide/tracks/{id}/snow-reports"})
    public ItemsResponse<SnowReportResponse> history(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return new ItemsResponse<>(reports.history(userId(jwt), id));
    }

    @PostMapping({"/api/admin/tracks/{id}/snow-reports", "/api/guide/tracks/{id}/snow-reports"})
    @ResponseStatus(HttpStatus.CREATED)
    public SnowReportResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                     @Valid @RequestBody SnowReportRequest req) {
        return reports.create(userId(jwt), id, req);
    }

    @PostMapping(path = {"/api/admin/snow-reports/{id}/photo", "/api/guide/snow-reports/{id}/photo"},
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SnowReportResponse addPhoto(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                       @RequestPart("file") MultipartFile file) {
        return reports.addPhoto(userId(jwt), id, file);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
